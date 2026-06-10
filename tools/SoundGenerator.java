import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 程序化音效生成器 — 生成 CC0 战斗音效。
 * 用法: java SoundGenerator.java [output-dir]
 */
public class SoundGenerator {

    private static final int SAMPLE_RATE = 22050;
    private static final int BITS = 16;
    private static final int CHANNELS = 1;

    /** 写入 WAV 文件 (16-bit PCM mono) */
    private static void writeWav(String path, float[] samples) throws IOException {
        int dataSize = samples.length * 2;
        int fileSize = 44 + dataSize;

        ByteBuffer buf = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);
        // RIFF header
        buf.put("RIFF".getBytes());
        buf.putInt(fileSize - 8);
        buf.put("WAVE".getBytes());
        // fmt chunk
        buf.put("fmt ".getBytes());
        buf.putInt(16);           // chunk size
        buf.putShort((short) 1);  // PCM
        buf.putShort((short) CHANNELS);
        buf.putInt(SAMPLE_RATE);
        buf.putInt(SAMPLE_RATE * CHANNELS * BITS / 8);
        buf.putShort((short) (CHANNELS * BITS / 8));
        buf.putShort((short) BITS);
        // data chunk
        buf.put("data".getBytes());
        buf.putInt(dataSize);
        // samples
        for (float s : samples) {
            int val = Math.max(-32768, Math.min(32767, (int) (s * 32767)));
            buf.putShort((short) val);
        }

        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(buf.array());
        }
        System.out.println("  Generated: " + path);
    }

    /** 短正弦波 (hit) */
    private static float[] sineBurst(double freq, double durationSec, double envelopeHalf) {
        int n = (int) (SAMPLE_RATE * durationSec);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double env = 1.0;
            if (t < envelopeHalf) {
                env = t / envelopeHalf;                          // attack
            } else if (t > durationSec - envelopeHalf * 0.3) {
                env = (durationSec - t) / (envelopeHalf * 0.3); // release
            }
            s[i] = (float) (Math.sin(2 * Math.PI * freq * t) * env * 0.7);
        }
        return s;
    }

    /** 噪音 (dash/whoosh) */
    private static float[] noiseBurst(double durationSec, double envelopeHalf) {
        int n = (int) (SAMPLE_RATE * durationSec);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double env = Math.min(t / envelopeHalf, 1.0) * Math.max(0, (durationSec - t) / (envelopeHalf * 0.3));
            s[i] = (float) ((Math.random() * 2 - 1) * env * 0.5);
        }
        return s;
    }

    /** 混合两个信号 */
    private static float[] mix(float[] a, float[] b) {
        int n = Math.max(a.length, b.length);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            float va = i < a.length ? a[i] : 0;
            float vb = i < b.length ? b[i] : 0;
            s[i] = Math.max(-1, Math.min(1, va + vb));
        }
        return s;
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "assets/sounds";

        // 1. light_hit — 短高音 (sin 800Hz, 80ms)
        writeWav(dir + "/hit_light.wav", sineBurst(800, 0.08, 0.015));

        // 2. heavy_hit — 低频轰击 (sin 180Hz + noise, 250ms)
        writeWav(dir + "/hit_heavy.wav", mix(sineBurst(180, 0.25, 0.03), noiseBurst(0.25, 0.02)));

        // 3. special — 上升音 (sweep 200→800Hz, 400ms)
        int nSpecial = (int) (SAMPLE_RATE * 0.4);
        float[] sp = new float[nSpecial];
        for (int i = 0; i < nSpecial; i++) {
            double t = (double) i / SAMPLE_RATE;
            double freq = 200 + 600 * (t / 0.4);
            double env = Math.min(t / 0.05, 1.0) * Math.max(0, (0.4 - t) / 0.1);
            sp[i] = (float) (Math.sin(2 * Math.PI * freq * t) * env * 0.6);
        }
        writeWav(dir + "/special.wav", sp);

        // 4. dash — 风声 (filtered noise, 150ms)
        writeWav(dir + "/dash.wav", noiseBurst(0.15, 0.02));

        // 5. block — 短闷声 (sin 120Hz, 60ms)
        writeWav(dir + "/block.wav", sineBurst(120, 0.06, 0.01));

        // 6. ko — 下降音 (sweep 400→80Hz, 800ms)
        int nKo = (int) (SAMPLE_RATE * 0.8);
        float[] ko = new float[nKo];
        for (int i = 0; i < nKo; i++) {
            double t = (double) i / SAMPLE_RATE;
            double freq = 400 - 320 * (t / 0.8);
            double env = Math.max(0, 1.0 - t / 0.8);
            ko[i] = (float) (Math.sin(2 * Math.PI * freq * t) * env * 0.65);
        }
        writeWav(dir + "/ko.wav", ko);

        System.out.println("Done. Generated 6 sound files in " + dir);
    }
}
