package com.example.administrator.mfcc;

import android.os.Build;

import java.util.Arrays;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.util.fft.HammingWindow;

public class FeatureProcessor implements AudioProcessor {
    private FFT fft;
    private int samplesPerFrame;
    private float entropy;
    private float AMR;
    private float bandWidth;
    private float centroid;
    private float ATR;
    private int step,frame;



    public FeatureProcessor(int samplesPerFrame, int RMSstep, int RMSframe){
        this.samplesPerFrame=samplesPerFrame;
        this.step=RMSstep;
        this.frame=RMSframe;
        fft=new FFT(samplesPerFrame, new HammingWindow());
    }
    @Override
    public boolean process(AudioEvent audioEvent) {
        float[] sound=audioEvent.getFloatBuffer();
        float[] normal_sound=normalization(sound);
        float[] spectrum=magnitudeSpectrum(normal_sound);
        float[] rms=getRMSArray(normal_sound);
        entropy=spectralEntropy(spectrum);
        ATR=ATR(rms,5,rms.length);
        bandWidth=Bandwidth(spectrum);
        AMR=AMR(spectrum,(float)1.5);
        return false;
    }
    public float[] complement(float[] a,int length) {
        float[] result=new float[length];
        if(a.length<length){
            System.arraycopy(a, 0, result, 0, a.length);
            for(int i=a.length;i<length;i++)
                result[i]=0;
            return result;
        }else {
            return a;
        }
    }
    public float[] normalization(float[] a) {
        float max=Math.abs(a[0]);
        for(int i=1;i<a.length;i++) {
            float temp=Math.abs(a[i]);
            max=max>temp?max:temp;
        }
        for(int i=0;i<a.length;i++) {
            a[i]/=max;
        }
        return a;
    }
    public float[] magnitudeSpectrum(float frame[]){
        float magSpectrum[] = new float[frame.length];
        // calculate FFT for current frame

        fft.forwardTransform(frame);

        // calculate magnitude spectrum
        for (int k = 0; k < frame.length/2; k++){
            magSpectrum[frame.length/2+k] = fft.modulus(frame, frame.length/2-1-k);
            magSpectrum[frame.length/2-1-k] = magSpectrum[frame.length/2+k];
        }
        return magSpectrum;
    }
    public float spectralEntropy(float[] spectrum) {
        entropy=0;
        for(int i=0;i<spectrum.length;i++) {
            entropy+=spectrum[i]*Math.log(spectrum[i]);
        }
        entropy=-entropy;
        entropy/=1000000;
        return entropy*10;
    }
    public float spectralCentroid(float[] spectrum) {
         centroid=0;
        float a=0,b=0;
        for(int i=0;i<spectrum.length;i++) {
            a+=i*spectrum[i]*spectrum[i];
            b+=spectrum[i]*spectrum[i];
        }
        centroid=a/b;
        return centroid;
    }
    public float Bandwidth(float[] spectrum) {
        bandWidth=0;
        float cen=spectralCentroid(spectrum);
        float a=0,b=0;
        for(int i=0;i<spectrum.length;i++) {
            a+=(i-cen)*(i-cen)*spectrum[i]*spectrum[i];
            b+=spectrum[i]*spectrum[i];
        }
        bandWidth=a/b;
        return bandWidth/100000;
    }
    public float spectralRolloff(float[] spectrum) {
        float rate=0.93F;
        float threshold=0;
        for(int i=0;i<spectrum.length;i++) {
            threshold+=spectrum[i];
        }
        threshold*=rate;
        float curEnergy=0;
        int index=0;
        while(curEnergy<=threshold&&index<spectrum.length) {
            curEnergy+=spectrum[index];
            index++;
        }
        index--;
        return index;
    }

    public float[] getRMSArray(float[] signal) {
        int curPos=0;
        int index=0;
        int length=signal.length;
        float[] RMS=new float[(length+step-frame)/step+1];
        while(curPos+frame<length) {
            RMS[index]=RMS(signal, curPos, curPos+frame);
            curPos+=step;
            index++;
        }
        RMS[index]=RMS(signal, curPos, length);
        return RMS;
    }

    public float AMR(float[] RMS,float rate) {
        AMR=0;
        float avg=0;
        for(int i=0;i<RMS.length;i++) {
            avg+=RMS[i];
        }
        avg/=RMS.length;
        avg*=rate;
        for(int i=0;i<RMS.length;i++) {
            AMR+=RMS[i]>avg?1:0;
        }
        AMR/=RMS.length;
        return AMR*10;
    }

    public static float AC(float[] RMS) {
        return 0;
    }
    public float ATR(float[] RMS,int k,int length) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Arrays.parallelSort(RMS);
        }else{
            return 0;
        }
        ATR=0;
        for(int i=length-1;i>=length-k;i--) {
            ATR+=RMS[i];
        }
        ATR/=k;
        return ATR*10;
    }

    public float RMS(float[] signal,int begin,int end) {
        float RMS=0;
        for(int i=begin;i<end;i++) {
            RMS+=signal[i]*signal[i];
        }
        RMS=(float)Math.sqrt(RMS)/(end-begin);
        return RMS;
    }

    public float getAMR() {
        return AMR;
    }

    public float getBandWidth() {
        return bandWidth;
    }

    public float getATR() {
        return ATR;
    }

    public float getEntropy() {
        return entropy;
    }

    public float getCentroid() {
        return centroid;
    }

    @Override
    public void processingFinished() {

    }
}
