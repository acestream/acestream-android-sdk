package org.acestream.sdk;

public class OutputFormat {
    public String format;
    public boolean transcodeAudio;
    public boolean transcodeMP3;
    public boolean transcodeAC3;

    @Override
    public String toString() {
        return String.format("OuputFormat(format=%s audio=%s mp3=%s ac3=%s)",
                format,
                transcodeAudio,
                transcodeMP3,
                transcodeAC3);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OutputFormat))
            return false;
        if (obj == this)
            return true;

        OutputFormat rhs = (OutputFormat) obj;
        return (rhs.format.equals(this.format)
                && rhs.transcodeAudio == this.transcodeAudio
                && rhs.transcodeMP3 == this.transcodeMP3
                && rhs.transcodeAC3 == this.transcodeAC3);
    }
}
