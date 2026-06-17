package mydiary;

import javax.sound.sampled.*; //All audio classes (AudioFormat, AudioSystem etc)
import java.io.*; //File input/output (File, IOException, InputStream etc.)

public class MusicPlayer {
    private Clip clip;
    private boolean isPlaying = false;
    private float volume = 0.5f;

    public boolean loadAndPlay(String filePath) {
        try {
            stopMusic();
            File audioFile = new File(filePath);
            if (!audioFile.exists()) return false;

            AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile);
            clip = AudioSystem.getClip();
            clip.open(ais);
            setVolume(volume);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
            isPlaying = true;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stopMusic() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
        }
        isPlaying = false;
    }

    public void toggleMusic(String filePath) {
        if (isPlaying) {
            stopMusic();
        } else {
            loadAndPlay(filePath);
        }
    }

    public boolean isPlaying() { return isPlaying; }

    public void setVolume(float vol) {
        this.volume = vol;
        if (clip != null && clip.isOpen()) {
            try {
                FloatControl fc = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float min = fc.getMinimum();
                float max = fc.getMaximum();
                float gain = min + (max - min) * vol;
                fc.setValue(gain);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public float getVolume() { return volume; }
}