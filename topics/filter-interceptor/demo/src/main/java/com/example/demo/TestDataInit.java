package com.example.demo;

import com.example.demo.domain.item.Item;
import com.example.demo.domain.item.ItemRepository;
import com.example.demo.domain.member.Member;
import com.example.demo.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class TestDataInit {

    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;

    /**
     * 테스트용 데이터 추가
     */
    @PostConstruct
    public void init() {
        Item item1 = new Item();
        item1.setItemName("itemA");
        item1.setPrice(10000);
        item1.setQuantity(10);

        Item item2 = new Item();
        item2.setItemName("itemB");
        item2.setPrice(20000);
        item2.setQuantity(20);

        itemRepository.save(item1);
        itemRepository.save(item2);

        Member member = new Member();
        member.setLoginId("test");
        member.setPassword("test!");
        member.setName("tester");
        memberRepository.save(member);
    }
}