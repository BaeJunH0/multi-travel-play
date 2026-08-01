package demo.travel.member

import demo.travel.auth.CurrentUser
import demo.travel.user.User
import demo.travel.member.dto.UpdateRoleRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/trips/{tripId}/members")
class MemberController(private val memberService: MemberService) {

    @GetMapping
    fun getMembers(@CurrentUser user: User, @PathVariable tripId: UUID) =
        memberService.getMembers(tripId, user.id)

    @PatchMapping("/{userId}")
    fun updateRole(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: UpdateRoleRequest,
    ) = memberService.updateRole(tripId, userId, request.role, user.id)

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable userId: UUID,
    ) = memberService.removeMember(tripId, userId, user.id)
}
