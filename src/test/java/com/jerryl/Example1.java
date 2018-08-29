package com.jerryl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by jerry on 2018/8/28.
 */
public class Example1 {
    public static int count = 0;
    public static int clientTotal = 5000;
    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        final CountDownLatch countDownLatch = new CountDownLatch(clientTotal);
        for (int i = 0; i < clientTotal ; i++) {
            executorService.execute(() -> {
                try {
                    add();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                countDownLatch.countDown();
            });
        }

        countDownLatch.await();
        executorService.shutdown();
        System.out.println("count:" + count);
    }
    private static void add() {
        count++;
    }
}
