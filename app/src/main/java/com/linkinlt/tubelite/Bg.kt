package com.linkinlt.tubelite

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Chạy việc nặng ở luồng nền rồi trả kết quả về luồng giao diện. */
object Bg {
    private val pool = Executors.newFixedThreadPool(2)
    val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        pool.execute {
            val r = try { Result.success(work()) } catch (t: Throwable) { Result.failure(t) }
            main.post { done(r) }
        }
    }

    fun exec(work: () -> Unit) = pool.execute { try { work() } catch (_: Throwable) {} }
}
