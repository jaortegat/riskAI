package com.risk.controller;

import com.risk.config.MapLoader;
import com.risk.dto.MapInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for serving web pages.
 */
@Controller
@RequiredArgsConstructor
public class WebController {

    private final MapLoader mapLoader;

    /**
     * Home page - game lobby.
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }

    /**
     * Create game page.
     */
    @GetMapping("/create")
    public String createGame(Model model) {
        model.addAttribute("maps", mapLoader.getAvailableMaps().stream()
                .map(MapInfoDTO::fromDefinition)
                .toList());
        return "create";
    }

    /**
     * Game page.
     */
    @GetMapping("/game/{gameId}")
    public String game(@PathVariable String gameId, Model model) {
        model.addAttribute("gameId", gameId);
        return "game";
    }

    /**
     * Join game page.
     */
    @GetMapping("/join/{gameId}")
    public String joinGame(@PathVariable String gameId, Model model) {
        model.addAttribute("gameId", gameId);
        return "join";
    }
}
