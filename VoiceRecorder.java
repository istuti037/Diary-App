package mydiary;

import javax.sound.sampled.*; //input, output, wav etc
import java.io.*; //Creates/accesses WAV file, hsndle errors

public class VoiceRecorder {
    private TargetDataLine targetLine;
    private boolean isRecording = false;
    private Thread  recordThread;
    private String  lastRecordingPath;

    // Mono 44100 Hz 16-bit (most compatible format)
    private static final AudioFormat RECORD_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);

    //Recording
    public boolean startRecording(String outputPath) {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, RECORD_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Microphone line not supported.");
                return false;
            }
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(RECORD_FORMAT);
            targetLine.start();
            isRecording = true;
            lastRecordingPath = outputPath;

            recordThread = new Thread(() -> {
                try {
                    new File(outputPath).getParentFile().mkdirs();
                    AudioInputStream ais = new AudioInputStream(targetLine);
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(outputPath));
                    System.out.println("Saved: " + outputPath);
                } catch (IOException ex) {
                    System.out.println("Save error: " + ex.getMessage());
                }
            });
            recordThread.start();
            return true;

        } catch (LineUnavailableException ex) {
            System.out.println("Mic unavailable: " + ex.getMessage());
            return false;
        }
    }

    public void stopRecording() {
        if (!isRecording || targetLine == null) return;
        isRecording = false;
        targetLine.stop();
        targetLine.close();
        try {
            if (recordThread != null) recordThread.join(4000); // wait for file write
        } catch (InterruptedException ignored) {}
        System.out.println("Recording stopped.");
    }

    public boolean isRecording()        { return isRecording; }
    public String  getLastRecordingPath(){ return lastRecordingPath; }

    //Playback using SourceDataLine
    public void playRecording(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) {
                System.out.println("File not found: " + path);
                return;
            }
            System.out.println("Playing: " + path + "  size=" + f.length() + " bytes");

            // Open the WAV file
            AudioInputStream rawAis = AudioSystem.getAudioInputStream(f);
            AudioFormat      srcFmt  = rawAis.getFormat();
            System.out.println("Source format: " + srcFmt);

            // Decode to PCM_SIGNED if necessary (e.g. ulaw, alaw)
            AudioFormat targetFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    srcFmt.getSampleRate(),
                    16,
                    srcFmt.getChannels(),
                    srcFmt.getChannels() * 2,
                    srcFmt.getSampleRate(),
                    false);

            AudioInputStream playAis;
            if (!srcFmt.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                playAis = AudioSystem.getAudioInputStream(targetFmt, rawAis);
            } else {
                playAis = rawAis;
                targetFmt = srcFmt; // use original if already PCM
            }

            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, targetFmt);
            if (!AudioSystem.isLineSupported(lineInfo)) {
                System.out.println("Playback line not supported for format: " + targetFmt);
                return;
            }

            SourceDataLine sdl = (SourceDataLine) AudioSystem.getLine(lineInfo);
            sdl.open(targetFmt);
            sdl.start();

            byte[] buf = new byte[4096];
            int    len;
            while ((len = playAis.read(buf)) != -1) {
                sdl.write(buf, 0, len);
            }

            sdl.drain();  // flush remaining bytes
            sdl.stop();
            sdl.close();
            playAis.close();
            System.out.println("Playback complete.");

        } catch (Exception ex) {
            System.out.println("Playback error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
