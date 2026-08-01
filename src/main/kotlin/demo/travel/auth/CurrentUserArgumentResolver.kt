package demo.travel.auth

import demo.travel.auth.JwtFilter.Companion.USER_ID_ATTRIBUTE
import demo.travel.user.User
import demo.travel.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Component
class CurrentUserArgumentResolver(
    private val userRepository: UserRepository,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: org.springframework.core.MethodParameter) =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
                parameter.parameterType == User::class.java

    override fun resolveArgument(
        parameter: org.springframework.core.MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): User {
        val userId = webRequest.getAttribute(USER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST) as? UUID
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

        return userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }
}
