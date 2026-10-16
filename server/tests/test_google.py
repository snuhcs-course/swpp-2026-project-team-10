# AI-generated with Claude Code, 2026-10-15, reviewed by Jaewan Park
"""The real Google ID token check, against keys served by a fake of Google's certificate endpoint. The tokens are
signed here with a key made for the test, in the form Google uses.

Partitions:
- token: valid; another audience; another issuer; expired; signed with an unpublished key; no subject; not a JWT
- configuration: no client ID
- Google's keys: fetched once and kept for max-age; fetched again after it; no max-age; the endpoint failing or
  unreachable
"""

import time
from datetime import UTC, datetime, timedelta

import httpx2
import pytest
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from google.auth import crypt, jwt

from pix_server.accounts.google import CERTS_URL, GoogleTokenVerifier, GoogleUnavailable, InvalidGoogleToken

CLIENT_ID = "test-client.apps.googleusercontent.com"


def make_key(kid: str) -> tuple[crypt.RSASigner, str]:
    """A signer and the certificate Google would publish for it."""
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "test")])
    now = datetime.now(UTC)
    certificate = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(1)
        .not_valid_before(now - timedelta(days=1))
        .not_valid_after(now + timedelta(days=1))
        .sign(key, hashes.SHA256())
    )
    private = key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    )
    signer = crypt.RSASigner.from_string(private, key_id=kid)
    return signer, certificate.public_bytes(serialization.Encoding.PEM).decode()


@pytest.fixture(scope="module")
def keys():
    return {"published": make_key("kid-1"), "unpublished": make_key("kid-2")}


class CertsEndpoint:
    """Google's certificate endpoint: counts requests and answers with the published key."""

    def __init__(self, certificate: str) -> None:
        self.certificate = certificate
        self.requests = 0
        self.status = 200
        self.cache_control: str | None = "public, max-age=100"
        self.unreachable = False

    async def __call__(self, request: httpx2.Request) -> httpx2.Response:
        assert str(request.url) == CERTS_URL
        self.requests += 1
        if self.unreachable:
            raise httpx2.ConnectError("unreachable", request=request)
        headers = {"cache-control": self.cache_control} if self.cache_control else {}
        return httpx2.Response(self.status, json={"kid-1": self.certificate}, headers=headers)


@pytest.fixture
def endpoint(keys):
    return CertsEndpoint(keys["published"][1])


@pytest.fixture
async def make_verifier(endpoint, clock):
    clients = []

    def make(client_id: str | None = CLIENT_ID) -> GoogleTokenVerifier:
        client = httpx2.AsyncClient(transport=httpx2.MockTransport(endpoint))
        clients.append(client)
        return GoogleTokenVerifier(client_id, client, clock)

    yield make
    for client in clients:
        await client.aclose()


def token(keys, key="published", **overrides) -> str:
    now = int(time.time())
    claims = {
        "iss": "https://accounts.google.com",
        "aud": CLIENT_ID,
        "sub": "110169484474386276334",
        "name": "Junhyeong Han",
        "iat": now,
        "exp": now + 3600,
    }
    claims.update(overrides)
    claims = {name: value for name, value in claims.items() if value is not None}
    return jwt.encode(keys[key][0], claims).decode()


async def test_valid_token_gives_the_subject_and_the_name(make_verifier, keys):
    account = await make_verifier().verify(token(keys))
    assert (account.subject, account.name) == ("110169484474386276334", "Junhyeong Han")


async def test_issuer_without_the_scheme_is_also_google(make_verifier, keys):
    assert (await make_verifier().verify(token(keys, iss="accounts.google.com", name=None))).name is None


@pytest.mark.parametrize(
    "overrides",
    [
        {"aud": "another-app.apps.googleusercontent.com"},
        {"iss": "https://evil.example.com"},
        {"iat": int(time.time()) - 7200, "exp": int(time.time()) - 3600},
        {"sub": None},
        {"key": "unpublished"},
    ],
)
async def test_token_not_meant_for_pix_is_invalid(make_verifier, keys, overrides):
    with pytest.raises(InvalidGoogleToken):
        await make_verifier().verify(token(keys, **overrides))


async def test_text_that_is_not_a_token_is_invalid(make_verifier):
    with pytest.raises(InvalidGoogleToken):
        await make_verifier().verify("not-a-jwt")


async def test_without_a_client_id_google_sign_in_is_unavailable(make_verifier, keys, endpoint):
    with pytest.raises(GoogleUnavailable):
        await make_verifier(client_id=None).verify(token(keys))
    assert endpoint.requests == 0


async def test_keys_are_fetched_once_and_again_after_max_age(make_verifier, keys, endpoint, clock):
    verifier = make_verifier()
    await verifier.verify(token(keys))
    clock.advance(99)
    await verifier.verify(token(keys))
    assert endpoint.requests == 1
    clock.advance(1)
    await verifier.verify(token(keys))
    assert endpoint.requests == 2


async def test_keys_without_max_age_are_kept_for_an_hour(make_verifier, keys, endpoint, clock):
    endpoint.cache_control = None
    verifier = make_verifier()
    await verifier.verify(token(keys))
    clock.advance(3599)
    await verifier.verify(token(keys))
    assert endpoint.requests == 1
    clock.advance(1)
    await verifier.verify(token(keys))
    assert endpoint.requests == 2


@pytest.mark.parametrize("failure", ["status", "unreachable"])
async def test_keys_that_cannot_be_fetched_make_google_unavailable(make_verifier, keys, endpoint, failure):
    if failure == "status":
        endpoint.status = 500
    else:
        endpoint.unreachable = True
    with pytest.raises(GoogleUnavailable):
        await make_verifier().verify(token(keys))
