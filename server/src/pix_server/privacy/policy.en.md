**Privacy Policy**

The Last Penguin team ("the team") runs the Pix app and handles its users' personal information as described below. This is a translation of the Korean policy; if the two differ, the Korean text applies.

- Effective: November 21, 2026
- Contact: csmking7@gmail.com

## 1. Personal information we handle, and why

| Information | When it is collected | Purpose |
| --- | --- | --- |
| Name (display name), Pix ID | Sign-up, Pix ID setup | Identifying the account; shown when friends find and invite you |
| Password (stored only as a hash) | Sign-up with a Pix ID | Signing in |
| Google account identifier | Signing in with Google | Signing in |
| Friendships, friend requests, invitations | Using friends and invitations | The friend list, invitations to share the camera |
| Push notification token (a device identifier) | Signing in | Alerts for invitations and friend requests |
| Sign-in sessions (hashes of refresh tokens) | Signing in | Keeping you signed in, detecting stolen sign-ins |
| Access records (IP address, time, request method and path, response status and size, the referring page, and the app or browser's name and version) | Each connection to the server | Fixing outages, limiting how often requests are made |
| Scene photo, pose description | When you ask for AI poses, only after you agree | Suggesting and generating AI poses |
| Error reports and app usage data (including the phone model, the Android version, and a random ID made for each install) | While you use the app, with "Share usage data" on | Fixing errors, improving quality |

Before an account is made, the sign-up screen tells you what is collected, why, how long it is kept, and who receives it, and asks for your agreement. Without it, no account is made, but the camera, pose guides, AI poses, and room codes all work without an account. Then no account information is collected. "Continue with Google" sends the Google sign-in to the Pix server to check whether a Pix account uses that Google account; if none does, nothing is kept, and an account is made only after you agree on "Sign up with Google".

## 2. What the server does not keep

- Reference photos and saved guides stay on your phone.
- The live view and the photos taken while sharing the camera go directly between the two phones, encrypted. Without a direct path, they pass through a relay, which cannot read them. The Pix server neither receives nor records them.
- A photo taken while sharing the camera is also saved to the Pix album on the other phone. A small copy of the guide's reference photo (at most 512 pixels on its long side) goes to the other phone as a preview, and is not saved.
- Scene photos and pose descriptions are not kept on the Pix server after they are sent to the AI services.
- Error reports and app usage data hold no photo, pose description, name, Pix ID, room code, or advertising ID.

## 3. How long we keep it, and deletion

| Information | Kept for |
| --- | --- |
| Name, Pix ID, password hash, Google account identifier, friendships, friend requests | Until the account is deleted |
| Invitations | Deleted a day after they are made |
| Push notification token | Until you sign out or delete the account |
| Sign-in sessions | 30 days after their last use, or until the account is deleted |
| Access records | 30 days in the log store (AWS CloudWatch). The copy on each server is deleted oldest first once it passes its size limit |
| Request counts (per IP address) | 1 hour |
| Error reports and app usage data | As Google Firebase's retention policy sets |

When you delete your account, its information, sign-in sessions, push notification tokens, friendships, friend requests, and invitations are deleted from the server at once. Information past its retention period is also deleted without delay. If the service ends, all account information is deleted.

## 4. Outside services and transfer abroad

Pix uses the services below, and personal information is transferred abroad in doing so.

| Recipient | Country | Information transferred | Purpose | When |
| --- | --- | --- | --- | --- |
| Amazon Web Services (server hosting) | Australia | Account information, friends and invitations, push notification tokens, access records | Running the Pix server, keeping access records | When you connect to the server |
| Google (Firebase Crashlytics, Analytics, Cloud Messaging, Google Sign-In) | United States | Error reports, app usage data, push notification tokens and alert contents (the inviter's name), Google account identifier | Collecting errors, usage analysis, sending alerts, signing in | While you use the app, when an alert is sent, when you sign in |
| OpenRouter and its AI model providers | United States and others | Scene photos, pose descriptions | Suggesting and generating AI poses | When you ask for AI poses |
| Cloudflare (relay) | The nearest location | The encrypted live view and photos (which it cannot read) | Relaying when no direct path exists | While sharing the camera |

- Every transfer uses an encrypted connection.
- A scene photo is sent only after you agree. Withdraw the agreement under "AI pose generation" in the settings, and none is sent until Pix asks again.
- Error reports and app usage data are not sent while "Share usage data" in the settings is off.
- The team does not sell personal information or use it for advertising, and gives it to no third party beyond the services above.

## 5. Your rights

- **Edit your name, sign out:** in the app's Settings › Pix account.
- **Delete your account:** in the app's Settings › Pix account, "Delete account" deletes it at once, after asking. Other phones signed in to the account are signed out. Guides and photos saved on your phone stay.
- **Withdraw the AI pose agreement, turn off usage data:** in the app's Settings › Privacy.
- **See or correct your personal information:** email the contact below. We act on a request without delay.
- **Camera and notification permissions:** turn them off at any time in your phone's settings.

## 6. How we keep it safe

- Traffic between the app and the server is encrypted with HTTPS.
- Passwords are stored only as hashes (argon2id), from which the original cannot be recovered.
- The live view and control messages are encrypted end to end between the two phones.
- Keys for outside services are kept only on the server.

## 7. Children's personal information

Pix does not accept sign-ups from children under 14. The sign-up screen asks you to confirm that you are 14 or older. If we learn that information about a child under 14 was collected, we delete it without delay.

## 8. Contact

- Privacy officer: the Last Penguin team
- Email: csmking7@gmail.com

## 9. Changes to this policy

This policy is in the app under Settings › Privacy › Privacy policy. When it changes, the changes and their effective date are posted here.
