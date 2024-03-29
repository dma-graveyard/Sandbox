/* Copyright 2016 Danish Maritime Authority.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.maritimecloud.identityregistry.security;

import java.security.Security;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.keycloak.adapters.springsecurity.KeycloakSecurityComponents;
import org.keycloak.adapters.springsecurity.client.KeycloakClientRequestFactory;
import org.keycloak.adapters.springsecurity.client.KeycloakRestTemplate;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.keycloak.adapters.springsecurity.filter.KeycloakAuthenticationProcessingFilter;
import org.keycloak.adapters.springsecurity.filter.KeycloakPreAuthActionsFilter;

@Configuration
@EnableWebSecurity
public class MultiSecurityConfig  {

    @Configuration
    @Order(2)
    public static class AdminWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Autowired
        DataSource dataSource;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                .addFilterBefore(new SimpleCorsFilter(), ChannelProcessingFilter.class)
                .csrf().disable() // Needed for the simple REST login
                .authorizeRequests()
                    .antMatchers(HttpMethod.POST, "/admin/api/org/apply").permitAll()
                    .antMatchers(HttpMethod.POST, "/admin/api/**").hasRole("ADMIN")
                    .antMatchers(HttpMethod.PUT, "/admin/api/**").hasRole("ADMIN")
                    .antMatchers(HttpMethod.DELETE, "/admin/api/**").hasRole("ADMIN")
                    .antMatchers(HttpMethod.GET, "/admin/api/**").hasRole("ADMIN")
                    //.anyRequest().denyAll()
            .and()
                .formLogin()
                    // This will make a successful login return HTTP 200
                    .successHandler(new RestAuthenticationSuccessHandler())
                    // This will make a failed login return HTTP 401 (because a failed redirect url isn't given)
                    .failureHandler(new SimpleUrlAuthenticationFailureHandler())
                    .permitAll()
            .and()
                .logout().permitAll()
            ;
        }

        @Autowired
        public void configAuthentication(AuthenticationManagerBuilder auth) throws Exception {
            auth.jdbcAuthentication().dataSource(dataSource)
                    .usersByUsernameQuery("SELECT short_name, password_hash, 1 FROM organizations WHERE short_name=?")
                    .passwordEncoder(new BCryptPasswordEncoder())
                    .authoritiesByUsernameQuery("SELECT ?, 'ROLE_ADMIN' FROM DUAL")
                    // Assign ROLE_ADMIN and ROLE_LOCAL to the user
                    //.authoritiesByUsernameQuery("SELECT * FROM (SELECT ?) u, (SELECT 'ROLE_ADMIN' UNION SELECT 'ROLE_LOCAL') r;")
            //.authoritiesByUsernameQuery( "select username, role from user_roles where username=?")
            ;
        }
    }


    @Configuration
    @Order(1)
    @ComponentScan(basePackageClasses = KeycloakSecurityComponents.class)
    public static class OIDCWebSecurityConfigurationAdapter extends KeycloakWebSecurityConfigurerAdapter
    {
        // Enables client to client communication
        @Autowired
        public KeycloakClientRequestFactory keycloakClientRequestFactory;

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public KeycloakRestTemplate keycloakRestTemplate() {
            return new KeycloakRestTemplate(keycloakClientRequestFactory);
        }

        /**
         * Registers the KeycloakAuthenticationProvider with the authentication manager.
         */
        @Autowired
        public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
            auth.authenticationProvider(keycloakAuthenticationProvider());
        }

        /**
         * Defines the session authentication strategy.
         */
        @Bean
        @Override
        protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
            // Change from RegisterSessionAuthenticationStrategy to NullAuthenticatedSessionStrategy
            // if changing from confidential to bearer-only
            return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception
        {
            super.configure(http);
            http
                .requestMatchers()
                    .antMatchers("/oidc/**","/sso/**") // "/sso/**" matches the urls used by the keycloak adapter
            .and()
                .authorizeRequests()
                    .antMatchers(HttpMethod.POST, "/oidc/api/**").hasRole("ADMIN")
                    .antMatchers(HttpMethod.PUT, "/oidc/api/**").hasRole("ADMIN")
                    .antMatchers(HttpMethod.DELETE, "/oidc/api/**").hasRole("ADMIN")
                    .antMatchers(HttpMethod.GET, "/oidc/api/**").hasRole("USER")
        ;
        }

        @Bean
        public FilterRegistrationBean keycloakAuthenticationProcessingFilterRegistrationBean(
                KeycloakAuthenticationProcessingFilter filter) {
            FilterRegistrationBean registrationBean = new FilterRegistrationBean(filter);
            registrationBean.setEnabled(false);
            return registrationBean;
        }

        @Bean
        public FilterRegistrationBean keycloakPreAuthActionsFilterRegistrationBean(
                KeycloakPreAuthActionsFilter filter) {
            FilterRegistrationBean registrationBean = new FilterRegistrationBean(filter);
            registrationBean.setEnabled(false);
            return registrationBean;
        }
    }

    // See https://docs.spring.io/spring-security/site/docs/4.0.x/reference/html/x509.html
    // Needs some work to actually work!!
    @Configuration
    @Order(3)
    public static class X509WebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            // We should probably place this somewhere else...
            Security.addProvider(new BouncyCastleProvider());

            http
                .authorizeRequests()
                    .antMatchers(HttpMethod.POST, "/x509/api/**").authenticated()//.hasRole("USER")
                    .antMatchers(HttpMethod.PUT, "/x509/api/**").authenticated()//.hasRole("USER")
                    .antMatchers(HttpMethod.DELETE, "/x509/api/**").authenticated()//.hasRole("USER")
                    .antMatchers(HttpMethod.GET, "/x509/api/**").authenticated()//.hasRole("USER")
            .and()
                .x509()
                    .subjectPrincipalRegex("(.*)") // Extract all and let it be handled by the X509UserDetailsService. "CN=(.*?),"
                    .userDetailsService(x509UserDetailsService())
            ;
        }

        @Bean
        public X509UserDetailsService x509UserDetailsService() {
            return new X509UserDetailsService();
        }
    }
}
