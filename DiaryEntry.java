package mydiary;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DiaryEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String content;
    private String mood;
    private LocalDateTime dateTime;
    private String voiceNotePath;

    public DiaryEntry(String title, String content, String mood) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.title = title;
        this.content = content;
        this.mood = mood;
        this.dateTime = LocalDateTime.now();
        this.voiceNotePath = null;
    }

    public DiaryEntry(String id, String title, String content, String mood, LocalDateTime dateTime, String voiceNotePath) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.mood = mood;
        this.dateTime = dateTime;
        this.voiceNotePath = voiceNotePath;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }
    public LocalDateTime getDateTime() { return dateTime; }
    public String getVoiceNotePath() { return voiceNotePath; }
    public void setVoiceNotePath(String path) { this.voiceNotePath = path; }

    public String getFormattedDate() {
        return dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
    }

    public String getDateOnly() {
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    @Override
    public String toString() {
        return title + " | " + getFormattedDate() + " | " + mood;
    }
}
