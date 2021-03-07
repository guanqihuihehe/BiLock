package me.domin.bilock;

public interface TrainContract {
    interface View{
        void changeNum(int fileNum);
        void finishTrain();
        //为探测声音阈值增加的临时方法，debug后删除
        void updateMax(int max);

    }
    interface Presenter{
        void trainData(int type);
        void trainModel(int normal_type);
    }
}
