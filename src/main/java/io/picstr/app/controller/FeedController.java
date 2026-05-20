package io.picstr.app.controller;

import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import io.picstr.app.model.Photo;
import io.picstr.app.service.PhotoService;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class FeedController {

    private static final MediaType RSS_MEDIA_TYPE = MediaType.parseMediaType("application/rss+xml");

    private final PhotoService photoService;

    public FeedController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping(value = {"/feeds/recent.xml", "/feed/recent.xml"}, produces = "application/rss+xml")
    public ResponseEntity<String> recentFeed(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        List<Photo> photos = photoService.recentForFeed(limit);
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String xml = buildRss(baseUrl, photos);

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(RSS_MEDIA_TYPE)
            .body(xml);
    }

    private String buildRss(String baseUrl, List<Photo> photos) {
        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle("PicStr Recent Images");
        feed.setLink(baseUrl + "/");
        feed.setDescription("Recent uploaded images");

        List<SyndEntry> entries = new ArrayList<>(photos.size());
        for (Photo photo : photos) {
            String assetUrl = baseUrl + "/assets/thumb_" + photo.getInternalFilename();
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(photo.getOriginalFilename());
            entry.setPublishedDate(Date.from(photo.getUploadedAt()));
            entry.setUri("photo-" + photo.getId());
            if (photo.getUploadedAt() != null) {
                entry.setPublishedDate(Date.from(photo.getUploadedAt()));
            }

            SyndLink imageLink = new SyndLinkImpl();
            imageLink.setRel("alternate");
            imageLink.setType("image/*");
            imageLink.setHref(assetUrl);

            entry.setLinks(List.of(imageLink));
            entry.setLink(assetUrl);

            if (photo.getCategory() != null) {
                SyndCategory category = new SyndCategoryImpl();
                category.setName(photo.getCategory().toString());
                entry.setCategories(List.of(category));
            }

            entries.add(entry);
        }
        feed.setEntries(entries);

        try {
            return new SyndFeedOutput().outputString(feed);
        } catch (FeedException e) {
            throw new IllegalStateException("Failed to generate recent image RSS feed", e);
        }
    }
}
