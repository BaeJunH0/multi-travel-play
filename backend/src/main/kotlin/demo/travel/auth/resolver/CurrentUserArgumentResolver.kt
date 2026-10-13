package demo.travel.auth.resolver

import demo.travel.auth.filter.JwtFilter
import demo.travel.user.User
import demo.travel.user.UserRepository
import org.springframework.core.MethodParameter
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Component
class CurrentUserArgumentResolver(
    private val userRepository: UserRepository,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
                parameter.parameterType == User::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): User {
        val userId = webRequest.getAttribute(JwtFilter.USER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST) as? UUID
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

        return userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }
}