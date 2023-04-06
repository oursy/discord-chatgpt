package com.discord.chatgpt.discord.slash;

import java.util.concurrent.Semaphore;

public class ThreadPauseResume {

  private final Semaphore pauseSemaphore = new Semaphore(1);

  private boolean isPaused = false;

  public void pauseThread() {
    if (!isPaused) {
      try {
        pauseSemaphore.acquire();
        isPaused = true;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public void resumeThread() {
    if (isPaused) {
      pauseSemaphore.release();
      isPaused = false;
    }
  }

  public boolean isPaused() {
    return isPaused;
  }

  public Semaphore getPauseSemaphore() {
    return this.pauseSemaphore;
  }
}
