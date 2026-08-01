/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.system;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the PetClinic application.
 *
 * <p>
 * Permits anonymous read-only access to public-facing pages and requires the {@code USER}
 * role for all mutating (create/edit/delete) operations.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/",
						"/welcome",
						"/owners/find",
						"/owners",
						"/owners/{ownerId:\\d+}",
						"/vets",
						"/vets.html",
						"/oups",
						"/owners/*/pets/{petId:\\d+}",
						"/resources/**",
						"/webjars/**",
						"/css/**",
						"/images/**",
						"/fonts/**")
				.permitAll()
				.anyRequest()
				.hasRole("USER"))
			.formLogin(form -> form.defaultSuccessUrl("/owners/find", true).permitAll())
			.httpBasic(httpBasic -> {
			});
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		var user = User.builder()
			.username("admin")
			.password(passwordEncoder.encode("admin"))
			.roles("USER")
			.build();
		return new InMemoryUserDetailsManager(user);
	}

}
