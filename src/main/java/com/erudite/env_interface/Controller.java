package com.erudite.env_interface;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class Controller {
    @Autowired
    TKBService tkbService;

    /*
    Test endpoint returning fake algo info
     */
    @GetMapping("/algoInfo")
    public AlgoInfo getAlgoInfo(){
        return new AlgoInfo("POWER RULE", "O.a = I.a * I.n, O.x = I.x, O.n = I.n - 1");
    }


    /*
    True knowledge endpoint from Gemini for constructing concepts
     */
    @GetMapping("/instance")
    public ResponseEntity<Concept> getInstance() throws IOException {
        return ResponseEntity.ok(tkbService.getInstance());
    }

}