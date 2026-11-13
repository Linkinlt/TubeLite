package com.linkinlt.tubelite

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

object Errors {
    fun message(t: Throwable): String = when (t) {
        is SignInConfirmNotBotException -> "YouTube đang tạm chặn mạng này (nghi là máy tự động).\nChờ một lúc rồi thử lại, hoặc tắt bật lại modem wifi."
        is ReCaptchaException -> "YouTube đang tạm chặn, chờ vài phút rồi thử lại"
        is AgeRestrictedContentException -> "Video giới hạn độ tuổi, không xem được khi chưa đăng nhập"
        is GeographicRestrictionException -> "Video bị chặn ở Việt Nam"
        is ContentNotAvailableException -> "Video không xem được"
        is ExtractionException -> "YouTube vừa thay đổi, cần cập nhật ứng dụng"
        is IOException -> "Lỗi mạng, kiểm tra kết nối rồi thử lại"
        else -> "Có lỗi, thử lại sau"
    }

    fun maybeYoutubeChanged(t: Throwable) =
        t is ExtractionException && t !is ContentNotAvailableException && t !is ReCaptchaException
}
