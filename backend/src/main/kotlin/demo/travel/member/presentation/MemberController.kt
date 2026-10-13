package demo.travel.member.presentation

import demo.travel.auth.resolver.CurrentUser
import demo.travel.member.application.MemberService
import demo.travel.member.application.dto.MemberCommand
import demo.travel.member.presentation.dto.MemberRequest
import demo.travel.member.presentation.dto.MemberResponse
import demo.travel.user.User
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/trips/{tripId}/members")
class MemberController(private val memberService: MemberService) {

    @GetMapping
    fun getMembers(@CurrentUser user: User, @PathVariable tripId: UUID): List<MemberResponse> =
        memberService.getMembers(tripId, user.id).map { MemberResponse.of(it) }

    @PatchMapping("/{userId}")
    fun updateRole(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: MemberRequest.UpdateRole,
    ) = memberService.updateRole(
        MemberCommand.UpdateRole(tripId = tripId, targetUserId = userId, role = request.role, requesterId = user.id)
    )

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable userId: UUID,
    ) = memberService.removeMember(tripId, userId, user.id)
}
