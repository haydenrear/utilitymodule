package com.hayden.utilitymodule.db;

import com.hayden.utilitymodule.assert_util.AssertUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
public class DbDataSourceTrigger {

    public static final String APP_DB_KEY = "app_db_key";

    public static final String VALIDATION_DB_KEY = "validation_db_key";

    public interface SetKey {

        String curr();

        void setInit();

        void setInitialized();

        String starting();

        void setKey(String key);

        void resetKey();

    }

    private String currentKey = VALIDATION_DB_KEY;

    private final ThreadLocal<String> threadKey = new ThreadLocal<>();
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
    private final Object lock = new Object();

    public String setGlobalCurrentKey(String globalCurrent) {
        return doWithWriteLock(() -> this.currentKey = globalCurrent);
    }

    public String initializeKeyTo(String newKey) {
        if (countDownLatch.getCount() > 0) {
            synchronized (lock) {
                if (countDownLatch.getCount() > 0) {
                    doWithWriteLock(() -> {
                        countDownLatch.countDown();
                        this.setInitializedInner();
                        this.threadKey.set(newKey);
                        this.currentKey = newKey;
                    });
                    AssertUtil.assertTrue(() -> Objects.equals(newKey, this.currentKey),
                            "Global key was not correctly set in write lock.");
                    log.info("Initial global current key: {}", this.currentKey);
                }
            }
        }

        return this.currentKey();
    }

    /**
     * Must be able to be accessed - one writer at a time.
     * @return
     */
    public String currentKey() {
        String value = threadKey.get();
        return Optional.ofNullable(value)
                .orElseGet(() -> {
                    this.reentrantReadWriteLock.readLock().lock();
                    try {
                        return this.currentKey;
                    } finally {
                        this.reentrantReadWriteLock.readLock().unlock();
                    }
                });
    }


    public <T> T doOnKey(Function<SetKey, T> setKeyConsumer) {
        String prev = currentKey;
        try {
            this.threadKey.set(currentKey); // thread local makes no problem with deadlock!
            var toRet = setKeyConsumer.apply(new SetKey() {
                @Override
                public String curr() {
                    return threadKey.get();
                }

                @Override
                public void setInit() {
                    setValidationInner();
                }

                @Override
                public void setInitialized() {
                    setInitializedInner();
                }

                @Override
                public String starting() {
                    return prev;
                }

                @Override
                public void setKey(String key) {
                    doSetKey(key);
                }

                @Override
                public void resetKey() {
                    doSetKey(prev);
                }
            });
            return toRet;
        } finally {
            this.threadKey.remove();
        }
    }

    public String doWithKey(Consumer<SetKey> setKeyConsumer) {
        return doOnKey(sKey -> {
            setKeyConsumer.accept(sKey);
            return this.threadKey.get();
        });
    }

    public String doWithWriteLock(Runnable toDo) {
        reentrantReadWriteLock.writeLock().lock();
        try {
            toDo.run();
            return this.currentKey;
        } finally {
            reentrantReadWriteLock.writeLock().unlock();
        }
    }

    private void setValidationInner() {
        this.threadKey.set(VALIDATION_DB_KEY);
    }

    private void setInitializedInner() {
        this.threadKey.set(APP_DB_KEY);
    }

    private void doSetKey(String toSetTo) {
        this.threadKey.set(toSetTo);
    }

}
