package org.acestream.sdk.interfaces;

public interface IRemoteDevice {
    boolean isAceCast();
    void play();
    void pause();
    void stop(boolean disconnect);
    void setTime(long time);
    int setVolume(int volume);
    void setPosition(float position);
    boolean setAudioTrack(int track);
    boolean setSpuTrack(int track);
    void setVideoSize(String size);
    boolean setAudioDigitalOutputEnabled(boolean enabled);
    boolean setAudioOutput(String aout);
}
