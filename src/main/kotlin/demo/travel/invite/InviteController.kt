package demo.travel.invite

import demo.travel.auth.resolver.CurrentUser
import demo.travel.config.FrontendProperties
import demo.travel.user.User
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
class InviteController(
    private val inviteService: InviteService,
    private val frontendProperties: FrontendProperties,
) {

    @PostMapping("/api/trips/{tripId}/invite")
    fun createInviteLink(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
    ) = inviteService.createInviteLink(tripId, user.id, frontendProperties.url)

    @GetMapping("/api/invite/{shareToken}")
    fun getInviteInfo(@PathVariable shareToken: String) =
        inviteService.getInviteInfo(shareToken)

    @PostMapping("/api/invite/{shareToken}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    fun accept(@CurrentUser user: User, @PathVariable shareToken: String) =
        mapOf("tripId" to inviteService.accept(shareToken, user))
}
