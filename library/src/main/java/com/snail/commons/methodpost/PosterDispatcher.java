package com.snail.commons.methodpost;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 任务分配
 * <p>
 * date: 2019/8/7 10:18
 * author: zengfansheng
 */
public class PosterDispatcher {
    private final ThreadMode defaultMode;
    private final Poster backgroundPoster;
    private final Poster mainThreadPoster;
    private final ExecutorService executorService;
    private final Poster asyncPoster;

    public PosterDispatcher(@NonNull ExecutorService executorService, @NonNull ThreadMode defaultMode) {
        this.defaultMode = defaultMode;
        this.executorService = executorService;
        backgroundPoster = new BackgroundPoster(executorService);
        mainThreadPoster = new MainThreadPoster();
        asyncPoster = new AsyncPoster(executorService);
    }

    /**
     * 获取默认运行线程
     */
    public ThreadMode getDefaultMode() {
        return defaultMode;
    }

    /**
     * 获取线程池
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * 清除所有队列中任务，存在执行的无法停止
     */
    public void clearTasks() {
        backgroundPoster.clear();
        mainThreadPoster.clear();
        asyncPoster.clear();
    }

    /**
     * 根据方法上带的{@link RunOn}注解，将任务post到指定线程执行。如果方法上没有带注解，使用配置的默认值
     *
     * @param method   方法
     * @param runnable 要执行的任务
     */
    public void post(@Nullable Method method, @NonNull Runnable runnable) {
        if (method != null) {
            post(method, RunOn.class, runnable);
        }
    }

    /**
     * 根据方法上带的注解，将任务post到指定线程执行。如果方法上没有带注解，使用配置的默认值
     *
     * @param method   方法
     * @param cls      方法上注解的字节码
     * @param runnable 要执行的任务
     */
    public void post(@Nullable Method method, @NonNull Class<? extends Annotation> cls, @NonNull Runnable runnable) {
        if (method != null) {
            Annotation annotation = method.getAnnotation(cls);
            ThreadMode mode = defaultMode;
            if (annotation != null) {
                try {
                    Method m = annotation.getClass().getMethod("value");
                    Object value = m.invoke(annotation);
                    if (value instanceof ThreadMode) {
                        mode = (ThreadMode) value;
                    }
                } catch (Exception ignore) {
                }
            }
            post(mode, runnable);
        }
    }

    /**
     * 将任务post到指定线程执行。
     *
     * @param mode     指定任务执行线程
     * @param runnable 要执行的任务
     */
    public void post(@NonNull ThreadMode mode, @NonNull Runnable runnable) {
        if (mode == ThreadMode.UNSPECIFIED) {
            mode = defaultMode;
        }
        switch (mode) {
            case MAIN:
                mainThreadPoster.enqueue(runnable);
                break;
            case POSTING:
                runnable.run();
                break;
            case BACKGROUND:
                backgroundPoster.enqueue(runnable);
                break;
            case ASYNC:
                asyncPoster.enqueue(runnable);
                break;
        }
    }

    /**
     * 将任务post到指定线程执行
     *
     * @param owner      方法的所在的对象实例
     * @param methodName 方法名
     * @param parameters 参数信息
     */
    public void post(@NonNull Object owner, @NonNull String methodName, @Nullable MethodInfo.Parameter... parameters) {
        post(owner, methodName, RunOn.class, parameters);
    }

    /**
     * 将任务post到指定线程执行
     *
     * @param owner      方法的所在的对象实例
     * @param methodName 方法名
     * @param cls        方法上注解的字节码
     * @param parameters 参数信息
     */
    public void post(@NonNull final Object owner, @NonNull String methodName, @NonNull Class<? extends Annotation> cls,
                     @Nullable MethodInfo.Parameter... parameters) {
        if (parameters == null || parameters.length == 0) {
            try {
                final Method method = owner.getClass().getMethod(methodName);
                post(method, cls, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            method.invoke(owner);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception ignore) {
            }
        } else {
            final Object[] params = new Object[parameters.length];
            final Class<?>[] paramTypes = new Class[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                MethodInfo.Parameter parameter = parameters[i];
                params[i] = parameter.getValue();
                paramTypes[i] = parameter.getType();
            }
            try {
                final Method method = owner.getClass().getMethod(methodName, paramTypes);
                post(method, cls, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            method.invoke(owner, params);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * 将任务post到指定线程执行
     *
     * @param owner      方法的所在的对象实例
     * @param methodInfo 方法信息实例
     * @param cls        方法上注解的字节码
     */
    public void post(@NonNull Object owner, @NonNull MethodInfo methodInfo, @NonNull Class<? extends Annotation> cls) {
        post(owner, methodInfo.getName(), cls, methodInfo.getParameters());
    }

    /**
     * 将任务post到指定线程执行
     *
     * @param owner      方法的所在的对象实例
     * @param methodInfo 方法信息实例
     */
    public void post(@NonNull Object owner, @NonNull MethodInfo methodInfo) {
        post(owner, methodInfo.getName(), RunOn.class, methodInfo.getParameters());
    }
}
