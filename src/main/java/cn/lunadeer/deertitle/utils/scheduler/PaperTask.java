package cn.lunadeer.deertitle.utils.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class PaperTask implements CancellableTask {

    private final ScheduledTask task;

    public PaperTask(ScheduledTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        task.cancel();
    }
}
