package com.moasem.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class MoasemApplication

fun main(args: Array<String>) {
	runApplication<MoasemApplication>(*args)
}
