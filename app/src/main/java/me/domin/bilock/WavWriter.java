/* Copyright 2014 Eddy Xiao <bewantbe@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.domin.bilock;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
/*
    修改时间：2019/6/8
    修改人：July
    修改内容：修改写入wav的文件路径，在pushAudioShortNew方法中加入写wav文件的操作。
 */
class WavWriter {
    private static final String TAG = "WavWriter";
    public static final int MODEL=0;
    public static final int TEST=1;
    private File outPath;
    private OutputStream out;
    private byte[] header = new byte[44];
    private int state;

    private int channels = 1;
    private byte RECORDER_BPP = 16;  // bits per sample
    private int byteRate;            // Average bytes per second
    private int totalDataLen = 0;   // (file size) - 8
    private int totalAudioLen = 0;   // bytes of audio raw data
    //记录录音的长度，单位为帧，一帧所占字节数即为声音进度所对应的一单位的字节数（如精度为16bit，则一帧长度为16bit）
    private int framesWritten = 0;

    /**
        * @Title: WavWriter
    　　* @Description: 该构造函数初始化wav文件的头部信息，包括采样率，采样精度
    　　* @param  [state][sampleRate] state表示该对象用于处理训练的数据还是测试的数据  sampleRate为采样率
    　　* @return
    　　*/
    WavWriter(int state, int sampleRate) {
        this.state=state;
        byteRate = sampleRate * RECORDER_BPP / 8 * channels;

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1, PCM/uncompressed
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);              // Average bytes per second
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * RECORDER_BPP / 8);  // Block align (number of bytes per sample slice)
        header[33] = 0;
        header[34] = RECORDER_BPP;                          // bits per sample (Significant bits per sample)
        header[35] = 0;                                     // Extra format bytes
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
    }

    public byte[] getHeader() {
        return header;
    }

    private static final int version = android.os.Build.VERSION.SDK_INT;

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    double secondsLeft() {
        long byteLeft;
        if (version >= 9) {
            byteLeft = outPath.getFreeSpace();  // Need API level 9
        } else {
            StatFs statFs = new StatFs(outPath.getAbsolutePath());
            byteLeft = (statFs.getAvailableBlocks() * (long) statFs.getBlockSize());
        }
        if (byteRate == 0 || byteLeft == 0) {
            return 0;
        }
        return (double) byteLeft / byteRate;
    }
    /**
        * @Title: start()
    　　* @Description: 初始化wav文件的写入路径及输出流
    　　* @param
    　　* @return
    　　*/
    boolean start() {
        if (!isExternalStorageWritable()) {
            return false;
        }
        //File path = new File();
        //if (!path.exists() && !path.mkdirs()) {
         //   Log.e(TAG, "Failed to make directory: " + path.toString());
           // return false;
        //}
        //改wave名
        if(state==MODEL)
            outPath = new File(FileUtil.getFilePathName(FileUtil.MODEL_WAV));
        else if(state==TEST)
            outPath= new File(FileUtil.getFilePathName(FileUtil.TEST_WAV));
        File parent=outPath.getParentFile();
        parent.mkdirs();
        Log.e(TAG, "start: out " + outPath.exists() + " " + outPath.getName());

        try {
            out = new FileOutputStream(outPath);
            out.write(header, 0, 44);
            // http://developer.android.com/reference/android/os/Environment.html#getExternalStoragePublicDirectory%28java.lang.String%29
        } catch (IOException e) {
            Log.w(TAG, "start(): Error writing " + outPath, e);
            out = null;
        }
        return true;
    }

    /**
        * @Title: stop
    　　* @Description: 在录音结束修改wav头部中的文件长度以及数据长度信息
    　　* @param
    　　* @return
    　　*/
    void stop() {
        Log.e(TAG, "stop: out "+framesWritten);
        if (out == null) {
            Log.w(TAG, "stop(): Error closing " + outPath + "  null pointer");
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            Log.w(TAG, "stop(): Error closing " + outPath, e);
        }
        out = null;
        // Modify totalDataLen and totalAudioLen to match data
        RandomAccessFile raf;
        try {
            totalAudioLen = framesWritten * RECORDER_BPP / 8 * channels;
            totalDataLen=totalAudioLen+36;
            raf = new RandomAccessFile(outPath, "rw");
            raf.seek(4);
            raf.write((byte) ((totalDataLen) & 0xff));
            raf.write((byte) ((totalDataLen >> 8) & 0xff));
            raf.write((byte) ((totalDataLen >> 16) & 0xff));
            raf.write((byte) ((totalDataLen >> 24) & 0xff));
            raf.seek(40);
            raf.write((byte) ((totalAudioLen) & 0xff));
            raf.write((byte) ((totalAudioLen >> 8) & 0xff));
            raf.write((byte) ((totalAudioLen >> 16) & 0xff));
            raf.write((byte) ((totalAudioLen >> 24) & 0xff));
            raf.close();
        } catch (IOException e) {
            Log.w(TAG, "stop(): Error modifying " + outPath, e);
        }
        framesWritten=0;
    }

    private byte[] byteBuffer;

    // Assume RECORDER_BPP == 16 and channels == 1

    int max = 0;
    int index = 0;

    //MIN_NOISE:判断为牙齿咬合事件的声音最低值，MAX_NOISE：最高值
    public static int MIN_NOISE =1800;
    public static int MAX_NOISE = 30000;
    //截取声音信号的长度
    int bufferSize=300;
    //声音最大值前面的长度
    int backwardSize=100;
    //声音最大值后面的长度
    int forwardSize=200;
    LinkedBlockingQueue<int[]> queue = new LinkedBlockingQueue<>();
    //    int[] signal = new int[602];
    LinkedList<Integer> list = new LinkedList<>();
    int rest = 0;

    synchronized int pushAudioShortNew(short[] ss, int numOfReadShort) {

        int[] buffer = new int[ss.length];
        byte[] byteBuffer = new byte[ss.length * 2];
//        if (byteBuffer == null || byteBuffer.length != ss.length * 2) {
//            byteBuffer = new byte[ss.length * 2];
//        }
        int sum = 0;
        //将short数组转换成byte数组
        for (int i = 0; i < numOfReadShort; i++) {

            byteBuffer[2 * i] = (byte)(ss[i] & 0xff);
            byteBuffer[2 * i + 1] = (byte) ((ss[i] >> 8) & 0xff);

            int res = (byteBuffer[2 * i] & 0x000000FF) | ((int)(byteBuffer[2 * i + 1]) << 8);
            buffer[i] = res;
            sum += Math.abs(res);
            if (max < res) {
                max = res;
                index = i;
            }
        }
        Log.e(TAG, "pushAudioShortNew: temp max="+max);


        int average = sum / buffer.length;

        try{
            out.write(byteBuffer);
            //排除初始的一段杂音
            framesWritten += numOfReadShort;
            if(framesWritten<10000)
                return max;
        }catch(IOException e){
            Log.d(TAG,"write error!",e);
        }

        if (queue.size() == 0) {
            queue.add(buffer);
            return max;
        }
        int[] bytesOld = queue.poll();

        //当需要向下一个buffer组取剩余的值
        if (rest != 0) {
            for (int i = 0; i <= rest; i++) {
                list.add(buffer[i]);
            }
            rest = 0;
            max = 0;
            return -1;
        }

        //大于阈值视为牙齿咬合声音
//        if (index - 1 < 0 )
//            if (index + 1 < buffer.length)
//        Log.e(TAG, "pushAudioShortNew: max = " + max);
//        Log.e(TAG, "pushAudioShortNew: average = " + average);
//        Log.e(TAG, "pushAudioShortNew: average * 1.5 = " + average * 13);

        if (index - 1 > 0 && index + 1 < buffer.length)
            if (max > MIN_NOISE && max > buffer[index - 1] && max > buffer[index + 1]) {
                if(max>MAX_NOISE){
                    max=0;
                    return max;
                }
            Log.e(TAG, "pushAudioShortNew: max = " + max);
            Log.e(TAG, "pushAudioShortNew: index = " + index);
//
//            for (int i = 0; i < buffer.length; i++) {
//                Log.e(TAG, "pushAudioShortNew: buffer = " + buffer[i]);
//            }
//            Log.e(TAG, "-----------------------------");

                if (index - backwardSize < 0) {
                    //往上一个数组取100-index个
                    for (int i = bytesOld.length -(backwardSize-index-1); i < bytesOld.length; i++) {
                        list.add(bytesOld[i]);
                    }
                    //剩下的index+200在当前数组取
                    for (int i = 0; i <= index + forwardSize; i++) {
                        list.add(buffer[i]);
                    }
                    max = 0;
                    rest=0;
                    return -1;
                    //大于当前数组大小
                } else if (index + forwardSize+1 > buffer.length) {
                    for (int i = index - backwardSize; i < buffer.length; i++) {
                        list.add(buffer[i]);
                    }
                    rest = index + forwardSize - buffer.length;
                    //当前足够
                } else {
                    for (int i = index - backwardSize + 1; i <= index + forwardSize; i++) {
                        list.add(buffer[i]);
                    }
                    max = 0;
                    rest =0;
                    return -1;
                }
            }

        queue.add(buffer);

        return max;
    }


    /**
        * @Title: write
    　　* @Description: for test, to write signal into wav file
    　　* @param
    　　* @return
    　　*/
    synchronized void write(int[] signal,int numOfReadShort){

        short[] ss=new short[signal.length];
        for(int i=0;i<signal.length;i++)
            ss[i]=(short)signal[i];
        byte[] byteBuffer=new byte[numOfReadShort*2];
        for (int i = 0; i < numOfReadShort; i++) {

            byteBuffer[2 * i] = (byte)(ss[i] & 0xff);
            byteBuffer[2 * i + 1] = (byte) ((ss[i] >> 8) & 0xff);

        }
        try{
            out.write(byteBuffer);
            framesWritten += numOfReadShort;
        }catch(IOException e){
        }
    }

    /**
        * @Title: getSignal
    　　* @Description:  返回提取的声音峰值段
    　　* @param
    　　* @return    int[]
    　　*/
    int[] getSignal() {
        int size = list.size();
        int[] singal = new int[size];
        for (int i = 0; i < size; i++) {
            singal[i] = list.get(i);
        }
        return singal;
    }

    double secondsWritten() {
        return (double) framesWritten / (byteRate * 8 / RECORDER_BPP / channels);
    }

    /* Checks if external storage is available for read and write */
    boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();  // Need API level 8
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    String getPath() {
        return outPath.getPath();
    }
    void setPathandStart(String path){
        outPath=new File(path);
        try {
            out = new FileOutputStream(outPath);
            out.write(header, 0, 44);
            // http://developer.android.com/reference/android/os/Environment.html#getExternalStoragePublicDirectory%28java.lang.String%29
        } catch (IOException e) {
            Log.w(TAG, "start(): Error writing " + outPath, e);
            out = null;
        }
    }
}
