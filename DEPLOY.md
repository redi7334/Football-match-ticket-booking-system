# Deploying the Football Ticket Booking System Online

Netlify cannot host this app — Netlify only supports static sites and Node.js/Go serverless functions, not long-running Java servers. This guide uses **Render.com**, which has a free tier that runs Java containers.

## What you need

- A free **GitHub** account (https://github.com)
- A free **Render** account (https://render.com) — sign in with GitHub
- The 9 files in this folder:
  - `Main.java`
  - `WebServer.java`
  - `Html.java`
  - `BookingSystem.java`
  - `Customer.java`
  - `Team.java`
  - `Match.java`
  - `Ticket.java`
  - `Dockerfile`
  - `.dockerignore`

## Step 1 — Push the project to GitHub

1. Create a new GitHub repository (e.g. `football-tickets`). Keep it public for the free Render tier.
2. From the folder containing the files, run:
   ```
   git init
   git add .
   git commit -m "Initial project"
   git branch -M main
   git remote add origin https://github.com/<your-username>/football-tickets.git
   git push -u origin main
   ```

## Step 2 — Create a Web Service on Render

1. Log in to https://render.com.
2. Click **New +** → **Web Service**.
3. Choose **Build and deploy from a Git repository** and connect your `football-tickets` repo.
4. Fill in the service settings:
   - **Name**: anything, e.g. `football-tickets`
   - **Region**: pick the one closest to you
   - **Branch**: `main`
   - **Runtime / Language**: Render will detect the `Dockerfile` automatically — choose **Docker** if asked
   - **Instance Type**: **Free**
5. Leave the build/start commands empty (the Dockerfile defines them).
6. Click **Create Web Service**.

Render will build the Docker image, run `javac *.java`, and start the server. After 2–4 minutes, your app will be live at:

```
https://football-tickets.onrender.com
```

(Render shows the exact URL on the service page.)

## Step 3 — Verify

Open the URL in your browser. You should see the green homepage with the navigation bar (Home, Customers, Teams, Matches, Tickets, Reports). Sample data is pre-loaded.

## Things to know about the free tier

- The service **goes to sleep after 15 minutes of inactivity**. The first request after sleep takes 30–60 seconds to wake it up. Subsequent requests are instant.
- Free tier gives 750 hours/month — more than enough for a course demo.
- Data is **in-memory only**, so it resets every time the service restarts (including after every git push and every wake-up). Sample data reloads automatically.

## Updating after changes

Any time you change the code:

```
git add .
git commit -m "describe what changed"
git push
```

Render auto-deploys on every push.

## Alternative platforms

If Render does not work for you, the same Dockerfile works on:

- **Railway** (https://railway.app) — free trial, then pay-as-you-go
- **Fly.io** (https://fly.io) — generous free allowance, more CLI-driven
- **Google Cloud Run** — free tier; deploy with `gcloud run deploy`

For all of them: connect your GitHub repo or run their CLI in this folder; the Dockerfile handles the rest.
