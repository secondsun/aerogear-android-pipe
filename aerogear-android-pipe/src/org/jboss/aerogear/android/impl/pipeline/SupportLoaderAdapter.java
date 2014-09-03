/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.android.impl.pipeline;

import java.net.URL;
import java.util.List;

import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.ReadFilter;
import org.jboss.aerogear.android.impl.pipeline.loader.support.AbstractSupportPipeLoader;
import org.jboss.aerogear.android.impl.pipeline.loader.support.SupportReadLoader;
import org.jboss.aerogear.android.impl.pipeline.loader.support.SupportRemoveLoader;
import org.jboss.aerogear.android.impl.pipeline.loader.support.SupportSaveLoader;
import org.jboss.aerogear.android.pipeline.LoaderPipe;
import org.jboss.aerogear.android.pipeline.Pipe;
import org.jboss.aerogear.android.pipeline.PipeHandler;
import org.jboss.aerogear.android.pipeline.PipeType;
import org.jboss.aerogear.android.pipeline.RequestBuilder;
import org.jboss.aerogear.android.pipeline.ResponseParser;
import org.jboss.aerogear.android.pipeline.support.AbstractFragmentActivityCallback;
import org.jboss.aerogear.android.pipeline.support.AbstractSupportFragmentCallback;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;

import com.google.common.base.Objects;
import com.google.common.collect.Multimap;
import org.jboss.aerogear.android.http.HeaderAndBody;
import org.jboss.aerogear.android.impl.reflection.Scan;
import org.jboss.aerogear.android.pipeline.AbstractActivityCallback;
import org.jboss.aerogear.android.pipeline.AbstractFragmentCallback;

/**
 * This class wraps a Pipe in an asynchronous Loader.
 *
 * This classes uses Loaders from android.support. If you do not need to support
 * Android devices &lt; version 3.0, consider using {@link LoaderAdapter}
 *
 */
@SuppressWarnings( { "rawtypes", "unchecked" })
public class SupportLoaderAdapter<T> implements LoaderPipe<T>, LoaderManager.LoaderCallbacks<HeaderAndBody> {

    private static final String TAG = SupportLoaderAdapter.class.getSimpleName();

    private Multimap<String, Integer> idsForNamedPipes;
    private final Fragment fragment;
    private final FragmentActivity activity;
    private final Handler handler;

    private static enum Methods {

        READ, SAVE, REMOVE
    }

    private final Context applicationContext;
    private final Pipe<T> pipe;
    private final LoaderManager manager;
    private final RequestBuilder<T> requestBuilder;
    private final ResponseParser<T> responseParser;
    /**
     * The name referred to in the idsForNamedPipes
     */
    private final String name;

    public SupportLoaderAdapter(Fragment fragment, Context applicationContext, Pipe<T> pipe, String name) {
        this.pipe = pipe;
        this.manager = fragment.getLoaderManager();
        this.requestBuilder = pipe.getRequestBuilder();
        this.applicationContext = applicationContext;
        this.name = name;
        this.handler = new Handler(Looper.getMainLooper());
        this.activity = null;
        this.fragment = fragment;
        this.responseParser = pipe.getResponseParser();
    }

    public SupportLoaderAdapter(FragmentActivity activity, Pipe<T> pipe, String name) {
        this.pipe = pipe;
        this.requestBuilder = pipe.getRequestBuilder();
        this.manager = activity.getSupportLoaderManager();
        this.applicationContext = activity.getApplicationContext();
        this.name = name;
        this.handler = new Handler(Looper.getMainLooper());
        this.activity = activity;
        this.fragment = null;
        this.responseParser = pipe.getResponseParser();
    }

    @Override
    public PipeType getType() {
        return pipe.getType();
    }

    @Override
    public URL getUrl() {
        return pipe.getUrl();
    }

    @Override
    public void read(Callback<List<T>> callback) {
        int id = Objects.hashCode(name, callback);

        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(FILTER, null);
        bundle.putSerializable(METHOD, Methods.READ);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public void read(ReadFilter filter, Callback<List<T>> callback) {
        int id = Objects.hashCode(name, filter, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(FILTER, filter);
        bundle.putSerializable(METHOD, Methods.READ);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public void save(T item, Callback<T> callback) {
        int id = Objects.hashCode(name, item, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(ITEM, requestBuilder.getBody(item));
        bundle.putString(SAVE_ID, Scan.findIdValueIn(item));
        bundle.putSerializable(METHOD, Methods.SAVE);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public void remove(String toRemoveId, Callback<Void> callback) {
        int id = Objects.hashCode(name, toRemoveId, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(REMOVE_ID, toRemoveId);
        bundle.putSerializable(METHOD, Methods.REMOVE);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public PipeHandler<T> getHandler() {
        return pipe.getHandler();
    }

    @Override
    public Loader<HeaderAndBody> onCreateLoader(int id, Bundle bundle) {
        this.idsForNamedPipes.put(name, id);
        Methods method = (Methods) bundle.get(METHOD);
        Callback callback = (Callback) bundle.get(CALLBACK);
        verifyCallback(callback);
        Loader loader = null;
        switch (method) {
        case READ: {
            ReadFilter filter = (ReadFilter) bundle.get(FILTER);
            loader = new SupportReadLoader(applicationContext, callback, pipe.getHandler(), filter, this);
        }
            break;
        case REMOVE: {
            String toRemove = Objects.firstNonNull(bundle.getString(REMOVE_ID), "-1");
            loader = new SupportRemoveLoader(applicationContext, callback, pipe.getHandler(), toRemove);
        }
            break;
        case SAVE: {
            byte[] data = bundle.getByteArray(ITEM);
            String dataId = bundle.getString(SAVE_ID);
            loader = new SupportSaveLoader(applicationContext, callback,
                        pipe.getHandler(), data, dataId);
        }
            break;
        }
        return loader;
    }

    @Override
    public RequestBuilder<T> getRequestBuilder() {
        return requestBuilder;
    }

    @Override
    public ResponseParser<T> getResponseParser() {
        return responseParser;
    }

    @Override
    public Class<T> getKlass() {
        return pipe.getKlass();
    }

    @Override
    public void onLoadFinished(Loader<HeaderAndBody> loader, final HeaderAndBody data) {
        if (!(loader instanceof AbstractSupportPipeLoader)) {
            Log.e(TAG, "Adapter is listening to loaders which it doesn't support");
            throw new IllegalStateException("Adapter is listening to loaders which it doesn't support");
        } else {
            final AbstractSupportPipeLoader<HeaderAndBody> supportLoader = (AbstractSupportPipeLoader<HeaderAndBody>) loader;
            Object object = null;
            if (!supportLoader.hasException() && data != null && data.getBody() != null) {
                object = extractObject(data, supportLoader);
            }

            handler.post(new CallbackHandler<T>(this, supportLoader, object));
        }
    }

    static class CallbackHandler<T> implements Runnable {

        private final SupportLoaderAdapter<T> adapter;
        private final AbstractSupportPipeLoader<T> modernLoader;
        private final Object data;

        public CallbackHandler(SupportLoaderAdapter<T> adapter,
                AbstractSupportPipeLoader loader, Object data) {
            super();
            this.adapter = adapter;
            this.modernLoader = loader;
            this.data = data;
        }

        @Override
        public void run() {
            if (modernLoader.hasException()) {
                final Exception exception = modernLoader.getException();
                Log.e(TAG, exception.getMessage(), exception);
                if (modernLoader.getCallback() instanceof AbstractSupportFragmentCallback) {
                    adapter.fragmentFailure(modernLoader.getCallback(), exception);
                } else if (modernLoader.getCallback() instanceof AbstractFragmentActivityCallback) {
                    adapter.activityFailure(modernLoader.getCallback(), exception);
                } else {
                    modernLoader.getCallback().onFailure(exception);
                }

            } else {

                if (modernLoader.getCallback() instanceof AbstractSupportFragmentCallback) {
                    adapter.fragmentSuccess(modernLoader.getCallback(), data);
                } else if (modernLoader.getCallback() instanceof AbstractFragmentActivityCallback) {
                    adapter.activitySuccess(modernLoader.getCallback(), data);
                } else {
                    modernLoader.getCallback().onSuccess((T) data);
                }
            }

        }
    }

    @Override
    public void onLoaderReset(Loader<HeaderAndBody> loader) {
        //Gotta do something, though I don't know what
    }

    @Override
    public void reset() {
        for (Integer id : this.idsForNamedPipes.get(name)) {
            Loader loader = manager.getLoader(id);
            if (loader != null) {
                manager.destroyLoader(id);
            }
        }
        idsForNamedPipes.removeAll(name);
    }

    @Override
    public void setLoaderIds(Multimap<String, Integer> idsForNamedPipes) {
        this.idsForNamedPipes = idsForNamedPipes;
    }

    private void fragmentSuccess(Callback<T> typelessCallback, Object data) {
        AbstractSupportFragmentCallback callback = (AbstractSupportFragmentCallback) typelessCallback;
        callback.setFragment(fragment);
        callback.onSuccess(data);
        callback.setFragment(null);
    }

    private void fragmentFailure(Callback<T> typelessCallback, Exception exception) {
        AbstractSupportFragmentCallback callback = (AbstractSupportFragmentCallback) typelessCallback;
        callback.setFragment(fragment);
        callback.onFailure(exception);
        callback.setFragment(null);
    }

    private void activitySuccess(Callback<T> typelessCallback, Object data) {
        AbstractFragmentActivityCallback callback = (AbstractFragmentActivityCallback) typelessCallback;
        callback.setFragmentActivity(activity);
        callback.onSuccess(data);
        callback.setFragmentActivity(null);
    }

    private void activityFailure(Callback<T> typelessCallback, Exception exception) {
        AbstractFragmentActivityCallback callback = (AbstractFragmentActivityCallback) typelessCallback;
        callback.setFragmentActivity(activity);
        callback.onFailure(exception);
        callback.setFragmentActivity(null);
    }

    private Object extractObject(HeaderAndBody data, AbstractSupportPipeLoader<HeaderAndBody> supportLoader) {
        List results = responseParser.handleResponse(data, getKlass());

        if (results == null || results.size() == 0) {
            return results;
        } else if (supportLoader instanceof SupportSaveLoader) {
            return results.get(0);
        } else {
            return results;
        }
    }

    private void verifyCallback(Callback<List<T>> callback) {
        if (callback instanceof AbstractFragmentActivityCallback) {
            if (activity == null) {
                throw new IllegalStateException("An AbstractFragmentActivityCallback was supplied, but there is no Activity.");
            }
        } else if (callback instanceof AbstractSupportFragmentCallback) {
            if (fragment == null) {
                throw new IllegalStateException("An AbstractSupportFragmentCallback was supplied, but there is no Fragment.");
            }
        } else if (callback instanceof AbstractActivityCallback) {
            throw new IllegalStateException("An AbstractActivityCallback was supplied, but this is the support Loader.");
        } else if (callback instanceof AbstractFragmentCallback) {
            throw new IllegalStateException("An AbstractFragmentCallback was supplied, but this is the support Loader.");
        }
    }
}
