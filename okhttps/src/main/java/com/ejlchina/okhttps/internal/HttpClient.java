package com.ejlchina.okhttps.internal;

import com.ejlchina.okhttps.*;
import okhttp3.*;
import okhttp3.WebSocket;

import java.nio.charset.Charset;
import java.util.*;


public class HttpClient implements HTTP {

    // OkHttpClient
    final OkHttpClient okClient;
    // 根URL
    final String baseUrl;
    // 媒体类型
    final Map<String, String> mediaTypes;
    // 执行器
    final TaskExecutor executor;
    // 预处理器
    final Preprocessor[] preprocessors;
    // 持有标签的任务
    final List<TagTask> tagTasks;
    // 最大预处理时间倍数（相对于普通请求的超时时间）
    final int preprocTimeoutTimes;
    // 编码格式
    final Charset charset;
    // 默认的请求体类型
    final String bodyType;

    public HttpClient(Builder builder) {
        this.okClient = builder.okClient();
        this.baseUrl = builder.baseUrl();
        this.mediaTypes = builder.getMediaTypes();
        this.executor = new TaskExecutor(okClient.dispatcher().executorService(),
                builder.mainExecutor(), builder.downloadListener(),
                builder.responseListener(), builder.exceptionListener(),
                builder.completeListener(), builder.msgConvertors());
        this.preprocessors = builder.preprocessors();
        this.preprocTimeoutTimes = builder.preprocTimeoutTimes();
        this.charset = builder.charset();
        this.bodyType = builder.bodyType();
        this.tagTasks = new LinkedList<>();
    }

    @Override
    public AsyncHttpTask async(String url) {
        return new AsyncHttpTask(this, urlPath(url, false));
    }

    @Override
    public SyncHttpTask sync(String url) {
        return new SyncHttpTask(this, urlPath(url, false));
    }

	@Override
	public WebSocketTask webSocket(String url) {
		return new WebSocketTask(this, urlPath(url, true));
	}
    
    @Override
    public int cancel(String tag) {
        if (tag == null) {
            return 0;
        }
        int count = 0;
        synchronized (tagTasks) {
        	Iterator<TagTask> it = tagTasks.iterator();
            while (it.hasNext()) {
                TagTask tagCall = it.next();
                // 只要任务的标签包含指定的Tag就会被取消
                if (tagCall.tag.contains(tag)) {
                    if (tagCall.canceler.cancel()) {
                        count++;
                    }
                    it.remove();
                } else if (tagCall.isExpired()) {
                    it.remove();
                }
            }
        }
        return count;
    }

    @Override
    public void cancelAll() {
        okClient.dispatcher().cancelAll();
        synchronized (tagTasks) {
        	tagTasks.clear();
        }
    }

    @Override
    public Call request(Request request) {
        return okClient.newCall(request);
    }

    @Override
    public WebSocket webSocket(Request request, WebSocketListener listener) {
        return okClient.newWebSocket(request, listener);
    }

    public OkHttpClient okClient() {
        return okClient;
    }

    public int preprocTimeoutMillis() {
        return preprocTimeoutTimes * (okClient.connectTimeoutMillis()
        		+ okClient.writeTimeoutMillis()
        		+ okClient.readTimeoutMillis());
    }

    public int getTagTaskCount() {
        return tagTasks.size();
    }

    public TagTask addTagTask(String tag, Cancelable canceler, HttpTask<?> task) {
        TagTask tagTask = new TagTask(tag, canceler, task);
        synchronized (tagTasks) {
        	tagTasks.add(tagTask);
        }
		return tagTask;
    }

    public void removeTagTask(HttpTask<?> task) {
    	synchronized (tagTasks) {
    		Iterator<TagTask> it = tagTasks.iterator();
            while (it.hasNext()) {
                TagTask tagCall = it.next();
                if (tagCall.task == task) {
                    it.remove();
                    break;
                }
                if (tagCall.isExpired()) {
                    it.remove();
                }
            }
    	}
    }

    public class TagTask {

        String tag;
        Cancelable canceler;
        HttpTask<?> task;
        long createAt;

        TagTask(String tag, Cancelable canceler, HttpTask<?> task) {
            this.tag = tag;
            this.canceler = canceler;
            this.task = task;
            this.createAt = System.nanoTime();
        }

        boolean isExpired() {
            // 生存时间大于10倍的总超时限值
            return System.nanoTime() - createAt > 1_000_000 * preprocTimeoutMillis();
        }

		public void setTag(String tag) {
			this.tag = tag;
		}

    }

    public MediaType mediaType(String type) {
        String mediaType = mediaTypes.get(type);
        if (mediaType != null) {
            return MediaType.parse(mediaType);
        }
        return MediaType.parse("application/octet-stream");
    }

    @Override
    public TaskExecutor executor() {
        return executor;
    }

    public void preprocess(HttpTask<?> httpTask, Runnable request, 
    		boolean skipPreproc, boolean skipSerialPreproc) {
    	if (preprocessors.length == 0 || skipPreproc) {
    		request.run();
    		return;
    	}
    	int index = 0;
    	if (skipSerialPreproc) {
    		while (index < preprocessors.length 
    				&& preprocessors[index] instanceof SerialPreprocessor) {
    			index++;
    		}
    	}
    	if (index < preprocessors.length) {
    		RealPreChain chain = new RealPreChain(preprocessors,
                    httpTask, request, index + 1, 
                    skipSerialPreproc);
            preprocessors[index].doProcess(chain);
    	} else {
    		request.run();
    	}
    }

    /**
     * 串行预处理器
     * @author Troy.Zhou
     */
    public static class SerialPreprocessor implements Preprocessor {

        // 预处理器
        private Preprocessor preprocessor;
        // 待处理的任务队列
        private Queue<PreChain> pendings;
        // 是否有任务正在执行
        private boolean running = false;

        public SerialPreprocessor(Preprocessor preprocessor) {
            this.preprocessor = preprocessor;
            this.pendings = new LinkedList<>();
        }

        @Override
        public void doProcess(PreChain chain) {
            boolean should = true;
            synchronized (this) {
                if (running) {
                    pendings.add(chain);
                    should = false;
                } else {
                    running = true;
                }
            }
            if (should) {
                preprocessor.doProcess(chain);
            }
        }

        public void afterProcess() {
            PreChain chain = null;
            synchronized (this) {
                if (pendings.size() > 0) {
                    chain = pendings.poll();
                } else {
                    running = false;
                }
            }
            if (chain != null) {
                preprocessor.doProcess(chain);
            }
        }

    }


    class RealPreChain implements Preprocessor.PreChain {

        private int index;

        private Preprocessor[] preprocessors;

        private HttpTask<?> httpTask;

        private Runnable request;

        private boolean noSerialPreprocess;
        
        public RealPreChain(Preprocessor[] preprocessors, HttpTask<?> httpTask, Runnable request, 
        		int index, boolean noSerialPreprocess) {
            this.index = index;		// index 大于等于 1
            this.preprocessors = preprocessors;
            this.httpTask = httpTask;
            this.request = request;
            this.noSerialPreprocess = noSerialPreprocess;
        }

        @Override
        public HttpTask<?> getTask() {
            return httpTask;
        }

        @Override
        public HTTP getHttp() {
            return HttpClient.this;
        }

        @Override
        public void proceed() {
        	if (noSerialPreprocess) {
        		while (index < preprocessors.length 
        				&& preprocessors[index] instanceof SerialPreprocessor) {
        			index++;
        		}
        	} else {
        		Preprocessor last = preprocessors[index - 1];
                if (last instanceof SerialPreprocessor) {
                    ((SerialPreprocessor) last).afterProcess();
                }
        	}
            if (index < preprocessors.length) {
                preprocessors[index++].doProcess(this);
            } else {
                request.run();
            }
        }

    }

    @Override
    public Builder newBuilder() {
        return new Builder(this);
    }

    private String urlPath(String urlPath, boolean websocket) {
        String fullUrl;
        if (urlPath == null) {
            if (baseUrl != null) {
                fullUrl = baseUrl;
            } else {
                throw new HttpException("在设置 BaseUrl 之前，您必须指定具体路径才能发起请求！");
            }
        } else {
            boolean isFullPath = urlPath.startsWith("https://")
                    || urlPath.startsWith("http://")
                    || urlPath.startsWith("wss://")
                    || urlPath.startsWith("ws://");
            if (isFullPath) {
                fullUrl = urlPath;
            } else if (baseUrl != null) {
                fullUrl = baseUrl + urlPath;
            } else {
                throw new HttpException("在设置 BaseUrl 之前，您必须使用全路径URL发起请求，当前URL为：" + urlPath);
            }
        }
        if (websocket && fullUrl.startsWith("http")) {
            return fullUrl.replaceFirst("http", "ws");
        }
        if (!websocket && fullUrl.startsWith("ws")) {
            return fullUrl.replaceFirst("ws", "http");
        }
        return fullUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Map<String, String> mediaTypes() {
        return mediaTypes;
    }

    public Preprocessor[] preprocessors() {
        return preprocessors;
    }

    public List<TagTask> tagTasks() {
        return tagTasks;
    }

    public int preprocTimeoutTimes() {
        return preprocTimeoutTimes;
    }

    public Charset charset() {
        return charset;
    }

    public String bodyType() {
        return bodyType;
    }


}
