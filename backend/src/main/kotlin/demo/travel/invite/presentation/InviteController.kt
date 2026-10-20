package demo.travel.invite.presentation

import demo.travel.auth.resolver.CurrentUser
import demo.travel.config.FrontendProperties
import demo.travel.invite.application.InviteService
import demo.travel.invite.presentation.dto.InviteResponse
import demo.travel.user.User
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class InviteController(private val inviteService: InviteService, private val frontendProperties: FrontendProperties) {

    @PostMapping("/api/trips/{tripId}/invite")
    fun createInviteLink(@CurrentUser user: User, @PathVariable tripId: UUID): InviteResponse.Link =
        InviteResponse.Link.of(inviteService.createInviteLink(tripId, user.id, frontendProperties.url))

    @GetMapping("/api/invite/{shareToken}")
    fun getInviteInfo(@PathVariable shareToken: String): InviteResponse.Info =
        InviteResponse.Info.of(inviteService.getInviteInfo(shareToken))

    @PostMapping("/api/invite/{shareToken}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    fun accept(@CurrentUser user: User, @PathVariable shareToken: String): InviteResponse.Accept =
        InviteResponse.Accept(tripId = inviteService.accept(shareToken, user))
}
