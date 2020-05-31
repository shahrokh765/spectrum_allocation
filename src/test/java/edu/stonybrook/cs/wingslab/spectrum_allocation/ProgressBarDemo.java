package edu.stonybrook.cs.wingslab.spectrum_allocation;

public class ProgressBarDemo {
    public static void main(String args[]) throws InterruptedException {
        int progressBarLength = 50;
        int count = 750;
        long beginTime = System.currentTimeMillis();
        for (int i = 1; i < count+1; i++){
            String p = "";
            int progress = (int)((float)i/count * progressBarLength);
            for (int j = 0; j < progress; j++)
                p += "=";
            for (int j = progress; j < progressBarLength; j++)
                p += " ";
            long duration = System.currentTimeMillis() - beginTime;
            int percent = (int)((float)i/count * 100);
            p += "| " + percent + "(" + duration + ":" + duration/i*(count - i) + ")\r";
            if ((double)i/count == 0.5)
                System.out.println("recalling");
            System.out.print(p);
            Thread.sleep(10);
        }
        System.out.print("done");
    }
}
