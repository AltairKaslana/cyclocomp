package id.cyclocomp;

/** Satu lap (putaran) dalam sebuah rekaman. */
public class Lap {
    public int index;
    public double distanceM;
    public long movingMs;
    public long elapsedMs;   // waktu total termasuk berhenti, saat lap ditutup

    public float avgSpeedMs() {
        return movingMs > 0 ? (float) (distanceM / (movingMs / 1000.0)) : 0f;
    }
}
