package com.gpo.cronpaw

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.gpo.cronpaw.core.account.Account
import com.gpo.cronpaw.core.net.WeiboClient
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.core.weibo.Emoticons
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class CronPawApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        com.gpo.cronpaw.core.debug.CrashLog.install(this)
        AppSettings.init(this)
        WeiboClient.init(this)
        Account.init(this)
        Emoticons.init(this)
        com.gpo.cronpaw.core.filter.FeedFilter.init(this)
        com.gpo.cronpaw.core.channels.Channels.init(this)
        com.gpo.cronpaw.core.channels.QuietFollows.init(this)
    }

    /**
     * Pictures get their own client: no cookie jar, a wide per-host limit (a
     * nine-grid fling hits one sinaimg host 30 times at once), and the
     * weibo.com Referer the sinaimg CDN insists on.
     */
    private val imageHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            })
            .addInterceptor { chain ->
                val b = chain.request().newBuilder()
                WeiboClient.mediaHeaders().forEach { (k, v) -> b.header(k, v) }
                chain.proceed(b.build())
            }
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttp }))
                add(AnimatedImageDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
