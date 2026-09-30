package io.picstr.app.controller;

import io.picstr.app.service.PhotoService;
import io.picstr.app.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
@RequestMapping("/")
public class IndexController extends BaseController {

    @Autowired
    private PhotoService photoService;

    @Autowired
    private TagService tagService;

    @GetMapping("")
    public String index(Model model) {
        model.addAttribute("latestPhotos", photoService.latest());
        model.addAttribute("categories", photoService.categories());
        model.addAttribute("allTags", tagService.list());
        model.addAttribute("bulkEnabled", true);
        return "index";
    }
}
