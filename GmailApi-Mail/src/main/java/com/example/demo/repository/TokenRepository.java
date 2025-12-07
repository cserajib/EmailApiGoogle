package com.example.demo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.OAuthToken;

public interface TokenRepository extends JpaRepository<OAuthToken, Long>  {
	
	Optional<OAuthToken> findByEmail(String email);

}
 