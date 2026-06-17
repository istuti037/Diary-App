# Diary-App
A personal Java desktop diary application with an AI assistant, background music, and voice note recording.

## Features:

- **Write & manage diary entries** — add, edit, delete, and search entries by keyword or date
- **AI Assistant** — powered by Groq (LLaMA 3.3) to correct grammar, summarize entries, and have supportive conversations
- **Music Player** — play background music while writing
- **Voice Recorder** — record and attach voice notes to diary entries
- **Persistent Storage** — saves entries to MySQL database, with automatic fallback to local file storage

## Requirements:

- Java 17 or higher
- MySQL (optional — app works without it using local file storage)
- A [Groq API key] (free)
- MySQL JDBC Driver (`mysql-connector-j`)

## Setup:

### 1. Clone the repository

### 2. Create `config.properties`

Create a file named `config.properties` in the project root:

```properties
groq.api.key=YOUR_GROQ_API_KEY_HERE
db.url=jdbc:mysql://localhost:3306/diarydb
db.user=your_mysql_username
db.password=your_mysql_password
```

> **Never share this file.**

### 3. Set up MySQL (optional)

If you want database storage, create a database:

```sql
CREATE DATABASE diarydb;
```

The app will automatically create the required table on first run. If MySQL is unavailable, the app will save entries locally to `diary_entries.dat` instead.

### 4. Compile and run

THANK YOU!
