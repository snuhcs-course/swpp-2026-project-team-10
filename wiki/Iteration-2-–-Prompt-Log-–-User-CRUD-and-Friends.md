Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]


## Server APIs (#50)

- [Jaewan, 10-15] "Now we are going to implement the server APIs for user CRUD/friends. What APIs do we have in plan?"
- [Jaewan, 10-15] "I think we need just normal signup/login not only google auth"
- [Jaewan, 10-15] "Let's add this. On the security side, hash passwords and add password requirements but let's not limit login attempts per address for now. and the user logged in this way should be able add google signin info, and when signing up with google we ask the user whether he/she has a pix normal account and allow them to connect to that."
- [Jaewan, 10-15] "we don't need email or forgotten password reset, but can we make a google-only account also add a password later?"
- [Jaewan, 10-15] "how is auth passed through apis? do we use jwt?"
- [Jaewan, 10-15] "let's just use jwt"
- [Jaewan, 10-15] "let's use a shorter token life with a refresh token. and are we authenticating also on the previous APIs (e.g. pose generation)? they shouldn't require login. and is the postgres db managed via docker compose? is the data mounted to the machine's storage? so that crashes or restarts don't delete the data?"
- [Jaewan, 10-15] "is it okay to not pretect the non-auth apis? like pose generation?"
- [Jaewan, 10-15] "first commit and push the current fixes, then add the global pose cap and the failed-join limit"
- [Jaewan, 10-15] "commit and push this. also, for env variables we've added PIX_GOOGLE_CLIENT_ID but the deploy doesn't automatically add this while it should be set. On each deploy, can we make it compare the .env.example and the real .env in the server and find the missing (uncommented) keys, add them if they have default values, and tell the user to add one if it doesn't have a default value? but i'm not sure if this is a good way because the variable orders can be like mixed up and the .env file can become messy"
- [Jaewan, 10-15] "can i just set PIX_GOOGLE_CLIENT_ID as a dummy value and get the read id when implementing #54?

## Android-side (#54)

- [Jaewan, 10-21] "Now let's implement #54. Give me your plan and anything i need to decide"
- [Jaewan, 10-21] "[…] Good?"
  > 1. When signed out, let's keep the room code feature and use it. But go to the friends tab first with both "Sign in to add friends" and "Use a room code instead"
  > 2. Do as recommended. But I think we may have to fix or add flows in R&S.
  > 3, 4. Do as recommended.
  > 5. Tell me how to do the Google Cloud setup.
  > 6. The wireframes are managed in https://claude.ai/artifact/GRLQMzirhDrBui9WWTQ1vm, draw them after the screens are built.
- [Jaewan, 10-21] "The web client ID is "572788944856-cr72pqlradqibnep626t672driie7537.apps.googleusercontent.com". Start now."
- [Jaewan, 10-21] "All four of us are registered as owners, while I (Jaewan Park) made the project. For the wireframes, do as you recommended"
- [Jaewan, 10-21] "I want to test this in Android Studio. What flows should I check?"
- [Jaewan, 10-22] "for the wireframe you created, you used real names. Can you also change them to test names?"
- [Jaewan, 10-22] "yes, switch those to test names too"
- [Jaewan, 10-22] "okay now tell me how I can reproduce your test videos on my own"
- [Jaewan, 10-22] "commit and push the current changes"
- [Jaewan, 10-22] "I want two more. (1) in signup, we should we able to check whether the ID is already taken by a small separate button. (2) In the friends screen, I want to see the list of sent requests too (maybe a small button like "Sent requests >" and if we press it we can manage the requests)"
- [Jaewan, 10-22] "also commit and push this"
- [Jaewan, 10-22] "is any change to the docs updated?"
- [Jaewan, 10-22] "commit and push this"
- [Jaewan, 10-22] "There are reviews from pkdje's Claude. tell me if we should fix them"
- [Jaewan, 10-22] "I fixed 4 and pushed it. Make the fixes 1-3"

