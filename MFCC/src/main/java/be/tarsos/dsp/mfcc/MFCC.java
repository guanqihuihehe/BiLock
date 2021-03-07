/*
*      _______                       _____   _____ _____  
*     |__   __|                     |  __ \ / ____|  __ \ 
*        | | __ _ _ __ ___  ___  ___| |  | | (___ | |__) |
*        | |/ _` | '__/ __|/ _ \/ __| |  | |\___ \|  ___/ 
*        | | (_| | |  \__ \ (_) \__ \ |__| |____) | |     
*        |_|\__,_|_|  |___/\___/|___/_____/|_____/|_|     
*                                                         
* -------------------------------------------------------------
*
* TarsosDSP is developed by Joren Six at IPEM, University Ghent
*  
* -------------------------------------------------------------
*
*  Info: http://0110.be/tag/TarsosDSP
*  Github: https://github.com/JorenSix/TarsosDSP
*  Releases: http://0110.be/releases/TarsosDSP/
*  
*  TarsosDSP includes modified source code by various authors,
*  for credits and info, see README.
* 
*/

package be.tarsos.dsp.mfcc;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.util.fft.HammingWindow;
import uk.me.berndporr.iirj.Butterworth;


public class MFCC implements AudioProcessor {

    private int amountOfCepstrumCoef; //Number of MFCCs per frame
    protected int amountOfMelFilters; //Number of mel filters (SPHINX-III uses 40)
    protected float lowerFilterFreq; //lower limit of filter (or 64 Hz?)
    protected float upperFilterFreq; //upper limit of filter (or half of sampling freq.?)
    
    float[] audioFloatBuffer;
    //Er zijn evenveel mfccs als er frames zijn!?
    //Per frame zijn er dan CEPSTRA coëficienten
    private float[] mfcc;
    int centerFrequencies[];

    private FFT fft;
    private int samplesPerFrame; 
    private float sampleRate;


    Butterworth butterworthLow,butterworthHigh;


    public MFCC(int samplesPerFrame, int sampleRate){
    	this(samplesPerFrame, sampleRate, 30, 30, 133.3334f, ((float)sampleRate)/2f,22);
    }

    public MFCC(int samplesPerFrame, float sampleRate, int amountOfCepstrumCoef, int amountOfMelFilters, float lowerFilterFreq, float upperFilterFreq,float lifterPara) {
        this.samplesPerFrame = samplesPerFrame; 
        this.sampleRate = sampleRate;
        this.amountOfCepstrumCoef = amountOfCepstrumCoef;
        this.amountOfMelFilters = amountOfMelFilters;
        this.fft = new FFT(samplesPerFrame, new HammingWindow());

        this.lifterPara=lifterPara;
        
        this.lowerFilterFreq = Math.max(lowerFilterFreq, 25);
        this.upperFilterFreq = Math.min(upperFilterFreq, sampleRate / 2);
        calculateFilterBanks();

        butterworthHigh=new Butterworth();
        butterworthLow=new Butterworth();
        butterworthHigh.highPass(6,sampleRate,10);
        butterworthLow.lowPass(6,sampleRate,15000);
    }

	@Override
	public boolean process(AudioEvent audioEvent) {
		audioFloatBuffer = audioEvent.getFloatBuffer().clone();

        float[] butterworth=butterworthFilter(audioFloatBuffer);
		//preemp
        float[] p=preemp(butterworth);

        // Magnitude Spectrum
        float bin[] = magnitudeSpectrum(p);
        // get Mel Filterbank

        try {
            FileWriter fw = new FileWriter(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Bilock/test.txt"));
            for(int i=0;i<bin.length;i++){
                fw.write(String.valueOf(bin[i]));
                fw.write("\r\n");
            }
            fw.flush();
            fw.close();
        }catch(IOException e){

        }
        float fbank[] = melFilter(bin, centerFrequencies);
        try {
            FileWriter fw = new FileWriter(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Bilock/fbank.txt"));
            for(int i=0;i<fbank.length;i++){
                fw.write(String.valueOf(fbank[i]));
                fw.write("\r\n");
            }
            fw.flush();
            fw.close();
        }catch(IOException e){

        }
        // Non-linear transformation
        float f[] = nonLinearTransformation(fbank);
        // Cepstral coefficients
        float cc[]= cepCoefficients(f);
        //lifter
        mfcc=lifter(cc);
        
		return true;
	}

	@Override
	public void processingFinished() {

	}

    /**
     * @Title: butterworthFilter
    　　* @Description: 滤波器去除噪音，保留10~15000Hz的声音
    　　* @param
    　　* @return
    　　*/
	public float[] butterworthFilter(float[] buffer){
        Butterworth butterworth=new Butterworth();
        butterworth.bandPass(6,sampleRate,7505,14990);
        float[] butter=new float[buffer.length];
        for(int i=0;i<buffer.length;i++){
            butter[i]=(float)butterworth.filter(buffer[i]);
        }
        return butter;
    }
    /**
    * @Title: preemp
　　* @Description: 预加重处理，α系数默认0.95
　　* @param
　　* @return
　　*/
	public float[] preemp(float buffer[]) {
        float preemp[]=new float[buffer.length];
        preemp[0]=buffer[0];
        for (int j = 1; j < preemp.length; j++)
            preemp[j] = (float)(buffer[j] - 0.95 * buffer[j - 1]);
        return preemp;
    }

    /**
     * computes the magnitude spectrum of the input frame<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param frame Input frame signal
     * @return Magnitude Spectrum array
     */
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
	
    /**
     * calculates the FFT bin indices<br> calls: none<br> called by:
     * featureExtraction
     *
     */
 
    public final void calculateFilterBanks() {
        /*centerFrequencies = new int[amountOfMelFilters + 2];

        centerFrequencies[0] = Math.round(lowerFilterFreq / sampleRate * samplesPerFrame);
        centerFrequencies[centerFrequencies.length - 1] = (int) (samplesPerFrame / 2);

        double mel[] = new double[2];
        mel[0] = freqToMel(lowerFilterFreq);
        mel[1] = freqToMel(upperFilterFreq);
        
        float factor = (float)((mel[1] - mel[0]) / (amountOfMelFilters + 1));
        //Calculates te centerfrequencies.
        for (int i = 1; i <= amountOfMelFilters; i++) {
            float fc = (inverseMel(mel[0] + factor * i) / sampleRate) * samplesPerFrame;
            centerFrequencies[i - 1] = Math.round(fc);
        }*/


        //修改 2019/8/9
        centerFrequencies = new int[amountOfMelFilters + 2];

        double mel[] = new double[2];
        mel[0] = freqToMel(lowerFilterFreq);
        mel[1] = freqToMel(upperFilterFreq);

        float factor = (float)((mel[1] - mel[0]) / (amountOfMelFilters + 1));
        //Calculates te centerfrequencies.
        for (int i = 0; i <= amountOfMelFilters+1; i++) {
            float fc = (inverseMel(mel[0] + factor * i) / sampleRate*2) * (samplesPerFrame/2+1);
            //float fc = (inverseMel(mel[0] + factor * i));
            centerFrequencies[i] = Math.round(fc);
        }

    }
    
	
    /**
     * the output of mel filtering is subjected to a logarithm function (natural logarithm)<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param fbank Output of mel filtering
     * @return Natural log of the output of mel filtering
     */
    public float[] nonLinearTransformation(float fbank[]){
        float f[] = new float[fbank.length];
      //  final float FLOOR = -50;
        
        for (int i = 0; i < fbank.length; i++){
            f[i] = (float) Math.log(fbank[i]);

            // check if ln() returns a value less than the floor
       //     if (f[i] < FLOOR) f[i] = FLOOR;
        }
        
        return f;
    }
    
    /**
     * Calculate the output of the mel filter<br> calls: none called by:
     * featureExtraction
     * @param bin The bins.
     * @param centerFrequencies  The frequency centers.
     * @return Output of mel filter.
     */
    public float[] melFilter(float bin[], int centerFrequencies[]) {
        /*float temp[] = new float[amountOfMelFilters + 2];

        for (int k = 1; k <= amountOfMelFilters; k++) {
            float num1 = 0, num2 = 0;

            float den = (centerFrequencies[k] - centerFrequencies[k - 1] + 1);

            for (int i = centerFrequencies[k - 1]; i <= centerFrequencies[k]; i++) {
                num1 += bin[i] * (i - centerFrequencies[k - 1] + 1);
            }
            num1 /= den;

            den = (centerFrequencies[k + 1] - centerFrequencies[k] + 1);

            for (int i = centerFrequencies[k] + 1; i <= centerFrequencies[k + 1]; i++) {
                num2 += bin[i] * (1 - ((i - centerFrequencies[k]) / den));
            }

            temp[k] = num1 + num2;
        }

        float fbank[] = new float[amountOfMelFilters];
        
        for (int i = 0; i < amountOfMelFilters; i++) {
            fbank[i] = temp[i + 1];
        }*/

        float fbank[] = new float[amountOfMelFilters];

        for (int k = 1; k <= amountOfMelFilters; k++) {
            float num1 = 0, num2 = 0;

            float den = (centerFrequencies[k] - centerFrequencies[k - 1]);

            float unit=(sampleRate/2)/samplesPerFrame;

            for (int i = centerFrequencies[k - 1]; i <= centerFrequencies[k]; i++) {
                num1 += bin[i] * (i - centerFrequencies[k - 1]);
            }
            num1 /= den;

            den = (centerFrequencies[k + 1] - centerFrequencies[k]);

            for (int i = centerFrequencies[k] + 1; i <= centerFrequencies[k + 1]; i++) {
                num2 += bin[i] * (1 - ((i - centerFrequencies[k]) / den));
            }

            fbank[k-1] = num1 + num2;
        }
        return fbank;
    }
    
    
    /**
     * Cepstral coefficients are calculated from the output of the Non-linear Transformation method<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param f Output of the Non-linear Transformation method
     * @return Cepstral Coefficients
     */
    public float[] cepCoefficients(float f[]){
        float cepc[] = new float[amountOfCepstrumCoef];
        
        for (int i = 0; i < cepc.length; i++){
            for (int j = 0; j < f.length; j++){
//                cepc[i] += f[j] * Math.cos(Math.PI * i / f.length * (j + 0.5));
                cepc[i] += f[j] * Math.cos(Math.PI * i / f.length * (j+1 - 0.5));
            }
            cepc[i]*=Math.sqrt(2.0/f.length);
        }
        
        return cepc;
    }

    public float lifterPara;

    public float[] lifter(float f[]){
        float[] cepLifter=new float[amountOfCepstrumCoef];
        for(int i=0;i<cepLifter.length;i++){
            cepLifter[i]=(float)(1+0.5*lifterPara*Math.sin(Math.PI*(double)i/lifterPara));
        }
        float[] CC=new float[amountOfCepstrumCoef];
        for(int i=0;i<amountOfCepstrumCoef;i++){
             CC[i]=cepLifter[i]*f[i];
        }
        return CC;

    }
    
//    /**
//     * calculates center frequency<br>
//     * calls: none<br>
//     * called by: featureExtraction
//     * @param i Index of mel filters
//     * @return Center Frequency
//     */
//    private static float centerFreq(int i,float samplingRate){
//        double mel[] = new double[2];
//        mel[0] = freqToMel(lowerFilterFreq);
//        mel[1] = freqToMel(samplingRate / 2);
//        
//        // take inverse mel of:
//        double temp = mel[0] + ((mel[1] - mel[0]) / (amountOfMelFilters + 1)) * i;
//        return inverseMel(temp);
//    }
    
    /**
     * convert frequency to mel-frequency<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param freq Frequency
     * @return Mel-Frequency
     */
    protected static float freqToMel(float freq){
       //return (float) (2595 * log10(1 + freq / 700));
       return (float) (1127 * Math.log(1 + freq / 700));
    }
    
    /**
     * calculates the inverse of Mel Frequency<br>
     * calls: none<br>
     * called by: featureExtraction
     */
    private static float inverseMel(double x) {
//        return (float) (700 * (Math.pow(10, x / 2595) - 1));
        return (float) (700 * (Math.exp( x / 1127) - 1));
    }
    
    /**
     * calculates logarithm with base 10<br>
     * calls: none<br>
     * called by: featureExtraction
     * @param value Number to take the log of
     * @return base 10 logarithm of the input values
     */
    protected static float log10(float value){
        return (float) (Math.log(value) / Math.log(10));
    }

	public float[] getMFCC() {
		return mfcc.clone();
	}

	public int[] getCenterFrequencies() {
		return centerFrequencies;
	}
}
