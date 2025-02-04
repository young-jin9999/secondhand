package com.SecondHand.item;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/item") // 공통 경로를 설정
public class ItemController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private S3Service s3Service;

    // 아이템 작성 폼 보여주기
    @GetMapping
    public String showWriteForm(Model model) {
        model.addAttribute("item", new Item());
        return "item";
    }

    // 아이템 제출
    @PostMapping
    public String submitItem(@ModelAttribute Item item, @RequestParam("imgFile") MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            try {
                String imgURL = s3Service.uploadFile(file);
                item.setImgURL(imgURL);
            } catch (RuntimeException e) {
                throw new RuntimeException("파일 업로드 중 오류 발생: " + e.getMessage(), e);
            }
        } else {
            throw new RuntimeException("파일이 비어있습니다.");
        }

        itemService.saveItem(item);
        return "redirect:/item/list";
    }

    // 아이템 리스트 보여주기
    @GetMapping("/list")
    public String listItems(Model model) {
        List<Item> items = itemService.getAllItems();
        model.addAttribute("items", items);
        return "itemList";
    }

    // 아이템 삭제
    @PostMapping("/delete")
    public String deleteItem(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        Item item = itemService.getItemById(id);
        if (item != null) {
            try {
                // S3에서 파일 삭제
                String fileName = item.getImgURL().substring(item.getImgURL().lastIndexOf("/") + 1); // 파일 이름 추출
                System.out.println("Deleting file: " + fileName);
                s3Service.deleteFile("SecondHand/" + fileName); // S3에서 파일 삭제

                itemService.deleteItem(id); // 데이터베이스에서 아이템 삭제


                redirectAttributes.addFlashAttribute("message", "아이템이 성공적으로 삭제되었습니다.");
            } catch (Exception e) {

                redirectAttributes.addFlashAttribute("error", "아이템 삭제 중 오류가 발생했습니다: " + e.getMessage());
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "아이템을 찾을 수 없습니다.");
        }
        return "redirect:/item/list";
    }

    // 상품 상세 정보 페이지로 이동
    @GetMapping("/detail/{id}")
    public String showItemDetail(@PathVariable Long id, Model model) {
        Item item = itemService.getItemById(id);
        if (item != null) {
            model.addAttribute("item", item);
            return "itemDetail"; // 디테일 페이지로 이동
        } else {
            model.addAttribute("error", "아이템을 찾을 수 없습니다.");
            return "itemList"; // 목록 페이지로 돌아감
        }
    }

    // S3 presigned URL 생성
    @GetMapping("/presigned-url")
    @ResponseBody
    public String getPresignedUrl(@RequestParam String filename) {
        var result = s3Service.createPreSignedUrl("SecondHand/" + filename);
        System.out.println(result);
        return result;
    }

    // 아이템 수정 폼 보여주기
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Item item = itemService.getItemById(id);
        if (item != null) {
            model.addAttribute("item", item);
            return "itemEdit"; // 수정 페이지로 이동
        } else {
            model.addAttribute("error", "아이템을 찾을 수 없습니다.");
            return "itemList"; // 목록 페이지로 돌아감
        }
    }

    // 아이템 수정 처리
    @PostMapping("/edit")
    public String updateItem(@ModelAttribute Item item, @RequestParam(value = "imgFile", required = false) MultipartFile file) throws IOException {
        Item existingItem = itemService.getItemById(item.getId());

        // 파일이 업로드된 경우
        if (file != null && !file.isEmpty()) {
            try {
                // S3에서 기존 이미지 삭제
                if (existingItem.getImgURL() != null) {
                    // URL에서 키 추출 (예: https://your-bucket.s3.amazonaws.com/SecondHand/...)
                    String fileName = existingItem.getImgURL().substring(existingItem.getImgURL().lastIndexOf("/") + 1);
                    System.out.println("Deleting file: " + fileName);
                    s3Service.deleteFile("SecondHand/" + fileName); // 키에 "SecondHand/" 추가
                }
                // 새 이미지 업로드
                String imgURL = s3Service.uploadFile(file);
                item.setImgURL(imgURL);
            } catch (RuntimeException e) {
                throw new RuntimeException("파일 업로드 중 오류 발생: " + e.getMessage(), e);
            }
        } else {
            // 파일이 업로드되지 않은 경우, 기존 이미지 URL 유지
            item.setImgURL(existingItem.getImgURL());
        }

        itemService.updateItem(item); // 업데이트 메서드 호출
        return "redirect:/item/list";
    }

    @PostMapping("/item/update")
    public String updateItem(@ModelAttribute Item item) {
        // 여기서 item.getId()가 null인지 확인
        if (item.getId() == null) {
            throw new IllegalArgumentException("Item ID must not be null");
        }
        itemService.updateItem(item);
        return "redirect:/item/list";
    }
}
