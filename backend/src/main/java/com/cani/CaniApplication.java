package com.cani;

import com.cani.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CaniApplication {

    public static void main(String[] args) {
        DotenvLoader.load();
        SpringApplication.run(CaniApplication.class, args);
    }
}
