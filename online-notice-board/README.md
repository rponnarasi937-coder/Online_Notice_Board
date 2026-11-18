# Online Notice Board (Pure Java)

A web-based notice board built with **only Java**: no frameworks, no external libraries, no build tool.
It uses the web server that ships with the JDK (`com.sun.net.httpserver.HttpServer`) and every page is generated from Java code.

## Features

- Register, log in and log out (salted PBKDF2 password hashing, session cookies)
- Post notices with a category, a priority (Normal / Important / Urgent) and an optional expiry date
- Search notices and filter by category; pinned notices always stay on top
- Edit and delete your own notices; the admin can edit, delete and pin any notice
- Expired notices are automatically hidden from the public board
- Data is saved in plain text files in the `data/` folder, so no database is needed
- Security basics: HTML escaping (XSS), CSRF tokens, HttpOnly cookies, login attempt limit, strict Content-Security-Policy

## Requirements

- JDK 17 or newer (check with `java -version`)
- Optional: VS Code with the **Extension Pack for Java**

## Run in VS Code

1. Unzip the project, then **File > Open Folder** and choose `online-notice-board`
2. Open `src/noticeboard/Main.java`
3. Click **Run** above `main` (or press `F5`)
4. Open <http://localhost:8080> in your browser

## Run from a terminal

```
Windows:        run.bat
Linux / macOS:  ./run.sh
```

Use another port with `run.bat 9090` or `./run.sh 9090` (or set the `PORT` environment variable).

## Default admin account

On the first run an admin account is created:

| Username | Password   |
|----------|------------|
| `admin`  | `admin123` |

To choose your own admin password, set the `ADMIN_PASSWORD` environment variable before the first run
(or delete the `data/` folder and start again). Three sample notices are added on the first run; delete them from the admin account whenever you like.

## Project structure

```
online-notice-board/
|-- src/noticeboard/
|   |-- Main.java     starts the app, creates the admin account on first run
|   |-- Server.java   routes, login sessions, all request handlers
|   |-- Html.java     builds every HTML page (and the CSS)
|   |-- Store.java    keeps users/notices in memory and saves them to data/
|   |-- Util.java     HTML escaping, form parsing, password hashing
|   |-- Notice.java   notice record
|   `-- User.java     user record
|-- run.bat / run.sh  compile and run
|-- .vscode/          VS Code settings and launch configuration
`-- data/             created automatically when the app runs (not committed to Git)
```

## Pages

| URL                  | What it does                                   |
|----------------------|------------------------------------------------|
| `/`                  | Notice board with search and category filter   |
| `/notice/view?id=..` | Read one notice                                |
| `/notice/new`        | Post a notice (login required)                 |
| `/notice/edit?id=..` | Edit a notice (owner or admin)                 |
| `/notice/delete?id=..` | Confirm and delete a notice (owner or admin) |
| `/login`, `/register`| Account pages                                  |

## Push to GitHub

```
git init
git add .
git commit -m "Online notice board in pure Java"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

## Notes

- The app serves plain HTTP on your own computer. If you ever put it on a public server, run it behind an HTTPS reverse proxy and add the `Secure` flag to the session cookie in `Server.startSession`.
- Run only one copy at a time: the notices are saved in files that are read when the app starts.
