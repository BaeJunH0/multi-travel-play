package demo.travel.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import io.kotest.core.spec.style.BehaviorSpec
import jakarta.persistence.Entity
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * docs/convention-dto.md 와 패키지 구조 규칙을 강제한다.
 * 규칙을 바꾸면 문서도 함께 고친다.
 */
class ArchitectureTest :
    BehaviorSpec({

        val classes: JavaClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("demo.travel")

        // 도메인 루트 패키지: demo.travel.{domain} (엔티티·리포지토리 자리)
        val domainRoot = "demo.travel.*"

        given("계층 의존 방향") {
            `when`("application 계층은") {
                then("presentation 계층을 참조하지 않는다") {
                    noClasses().that().resideInAPackage("..application..")
                        .should().dependOnClassesThat().resideInAPackage("..presentation..")
                        .check(classes)
                }
            }
            `when`("client 계층은") {
                then("application·presentation 계층을 참조하지 않는다") {
                    noClasses().that().resideInAPackage("..client..")
                        .should().dependOnClassesThat().resideInAnyPackage("..application..", "..presentation..")
                        .check(classes)
                }
            }
            `when`("presentation 계층은") {
                then("client 계층을 참조하지 않는다 (외부 연동은 서비스를 거친다)") {
                    noClasses().that().resideInAPackage("..presentation..")
                        .should().dependOnClassesThat().resideInAPackage("..client..")
                        .check(classes)
                }
                then("리포지토리를 직접 쓰지 않는다") {
                    noClasses().that().resideInAPackage("..presentation..")
                        .should().dependOnClassesThat().areAssignableTo(Repository::class.java)
                        .check(classes)
                }
            }
            `when`("엔티티와 리포지토리는") {
                then("application·presentation·client 계층을 참조하지 않는다") {
                    noClasses().that().areAnnotatedWith(Entity::class.java)
                        .or().areAssignableTo(Repository::class.java)
                        .should().dependOnClassesThat().resideInAnyPackage("..application..", "..presentation..", "..client..")
                        .check(classes)
                }
            }
            `when`("common 패키지는") {
                then("다른 도메인을 참조하지 않는다") {
                    noClasses().that().resideInAPackage("demo.travel.common..")
                        .should().dependOnClassesThat(
                            resideInAPackage("demo.travel..").and(not(resideInAPackage("demo.travel.common.."))),
                        )
                        .check(classes)
                }
            }
        }

        given("클래스 위치와 이름") {
            `when`("@RestController는") {
                then("presentation 패키지에 두고 이름은 Controller로 끝난다") {
                    classes().that().areAnnotatedWith(RestController::class.java)
                        .should().resideInAPackage("..presentation")
                        .andShould().haveSimpleNameEndingWith("Controller")
                        .check(classes)
                }
            }
            `when`("@RestControllerAdvice는") {
                then("presentation 또는 common.exception 패키지에 둔다") {
                    classes().that().areAnnotatedWith(RestControllerAdvice::class.java)
                        .should().resideInAnyPackage("..presentation", "demo.travel.common.exception")
                        .check(classes)
                }
            }
            `when`("@Service는") {
                then("application 패키지에 두고 이름은 Service로 끝난다") {
                    classes().that().areAnnotatedWith(Service::class.java)
                        .should().resideInAPackage("..application")
                        .andShould().haveSimpleNameEndingWith("Service")
                        .check(classes)
                }
            }
            `when`("Request·Response DTO는") {
                then("presentation.dto 패키지에 둔다 (공통 에러 응답 제외)") {
                    classes().that(topLevelNamedEndingWith("Request", "Response"))
                        .should().resideInAnyPackage("..presentation.dto", "demo.travel.common.exception")
                        .check(classes)
                }
            }
            `when`("Command·Query·Result DTO는") {
                then("application.dto 패키지에 둔다") {
                    classes().that(topLevelNamedEndingWith("Command", "Query", "Result"))
                        .should().resideInAPackage("..application.dto")
                        .check(classes)
                }
            }
            `when`("@Entity는") {
                then("도메인 루트 패키지에 둔다") {
                    classes().that().areAnnotatedWith(Entity::class.java)
                        .should().resideInAPackage(domainRoot)
                        .check(classes)
                }
            }
            `when`("리포지토리는") {
                then("도메인 루트 패키지에 두고 이름은 Repository로 끝난다") {
                    classes().that().areInterfaces().and().areAssignableTo(Repository::class.java)
                        .should().resideInAPackage(domainRoot)
                        .andShould().haveSimpleNameEndingWith("Repository")
                        .check(classes)
                }
            }
            `when`("@ConfigurationProperties는") {
                then("client 또는 config 패키지에 두고 이름은 Properties로 끝난다") {
                    classes().that().areAnnotatedWith(ConfigurationProperties::class.java)
                        .should().resideInAnyPackage("..client", "demo.travel.config")
                        .andShould().haveSimpleNameEndingWith("Properties")
                        .check(classes)
                }
            }
        }

        given("기타 규칙") {
            `when`("설정값은") {
                then("@Value 대신 @ConfigurationProperties 클래스로 주입받는다") {
                    noClasses().should().dependOnClassesThat().areAssignableTo(Value::class.java)
                        .check(classes)
                }
            }
            `when`("서비스의 public 메서드는") {
                then("엔티티를 반환하지 않는다 (Result로 변환해서 넘긴다)") {
                    methods().that().areDeclaredInClassesThat().areAnnotatedWith(Service::class.java)
                        .and().arePublic()
                        .should(notReturnEntity())
                        .check(classes)
                }
            }
            `when`("컨트롤러 메서드는") {
                then("Map을 반환하지 않는다 (Response DTO로 응답한다)") {
                    noMethods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController::class.java)
                        .should().haveRawReturnType(assignableTo(Map::class.java))
                        .check(classes)
                }
            }
        }
    })

private fun topLevelNamedEndingWith(vararg suffixes: String) =
    object : DescribedPredicate<JavaClass>("top level classes named *${suffixes.joinToString("|*")}") {
        override fun test(c: JavaClass) = c.isTopLevelClass && suffixes.any { c.simpleName.endsWith(it) }
    }

private fun notReturnEntity() = object : ArchCondition<JavaMethod>("not return an @Entity") {
    override fun check(method: JavaMethod, events: ConditionEvents) {
        val returned: Set<JavaClass> = method.returnType.allInvolvedRawTypes
        val entities = returned.filter { it.isAnnotatedWith(Entity::class.java) }
        if (entities.isNotEmpty()) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    "${method.fullName} returns entity ${entities.joinToString { it.simpleName }}",
                ),
            )
        }
    }
}
