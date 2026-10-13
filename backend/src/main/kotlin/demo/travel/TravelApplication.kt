package demo.travel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TravelApplication

fun main(args: Array<String>) {
    runApplication<TravelApplication>(*args)
}
