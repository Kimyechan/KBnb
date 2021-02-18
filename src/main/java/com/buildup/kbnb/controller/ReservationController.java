package com.buildup.kbnb.controller;


import com.buildup.kbnb.advice.exception.ReservationException;
import com.buildup.kbnb.dto.ApiResponse;
import com.buildup.kbnb.dto.reservation.*;
import com.buildup.kbnb.model.Payment;
import com.buildup.kbnb.model.Reservation;
import com.buildup.kbnb.model.room.BedRoom;
import com.buildup.kbnb.model.room.Room;
import com.buildup.kbnb.model.user.User;
import com.buildup.kbnb.security.CurrentUser;
import com.buildup.kbnb.security.UserPrincipal;
import com.buildup.kbnb.service.RoomService;
import com.buildup.kbnb.service.UserService;
import com.buildup.kbnb.service.reservation.ReservationService;
import com.buildup.kbnb.util.payment.model.request.Cancel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {
    private final UserService userService;
    private final RoomService roomService;
    private final ReservationService reservationService;

    @PostMapping(produces = MediaTypes.HAL_JSON_VALUE + ";charset=utf8")
    public ResponseEntity<?> registerReservation(@CurrentUser UserPrincipal userPrincipal,
                                                 @Valid @RequestBody ReservationRegisterRequest reservationRegisterRequest,
                                                 BindingResult error) throws Exception {
        if (error.hasErrors()) {
            throw new ReservationException("예약 등록 입력값이 잘못되었습니다");
        }

        User user = userService.findById(userPrincipal.getId());
        Room room = roomService.findById(reservationRegisterRequest.getRoomId());
        List<Reservation> reservationList = reservationService.findByRoomId(room.getId());

        LocalDate checkIn = reservationRegisterRequest.getCheckIn();
        LocalDate checkOut = reservationRegisterRequest.getCheckOut();
        checkStrangeDate(checkIn, checkOut);
        checkAvailableDate(reservationList, checkIn, checkOut);

        Reservation reservation = mapToReservation(room, reservationRegisterRequest, user);
        Payment payment = mapToPayment(reservationRegisterRequest);
        Reservation savedReservation = reservationService.processWithPayment(reservation, payment);

        URI location = linkTo(methodOn(ReservationController.class).registerReservation(userPrincipal, reservationRegisterRequest, error)).withSelfRel().toUri();

        ReservationRegisterResponse reservationResponse = mapToRegisterResponse(savedReservation);
        EntityModel<ReservationRegisterResponse> model = EntityModel.of(reservationResponse);
        model.add(Link.of("/docs/api.html#resource-reservation-register").withRel("profile"));
        model.add(linkTo(methodOn(ReservationController.class).registerReservation(userPrincipal, reservationRegisterRequest, error)).withSelfRel());

        return ResponseEntity.created(location)
                .body(model);
    }

    private ReservationRegisterResponse mapToRegisterResponse(Reservation savedReservation) {
        ReservationRegisterResponse reservationResponse = ReservationRegisterResponse.builder()
                .message("예약 성공")
                .reservationId(savedReservation.getId())
                .build();
        return reservationResponse;
    }

    private Payment mapToPayment(ReservationRegisterRequest reservationRegisterRequest) {
        return Payment.builder()
                .receiptId(reservationRegisterRequest.getPayment().getReceipt_id())
                .build();
    }

    private Reservation mapToReservation(Room room, ReservationRegisterRequest reservationRegisterRequest, User user) {
        return Reservation.builder()
                .room(room)
                .guestNum(reservationRegisterRequest.getGuestNumber())
                .checkOut(reservationRegisterRequest.getCheckOut())
                .checkIn(reservationRegisterRequest.getCheckIn())
                .totalCost(reservationRegisterRequest.getTotalCost())
                .user(user)
                .build();
    }

    private void checkStrangeDate(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn.isAfter(checkOut) || checkIn.isBefore(LocalDate.now()) || checkOut.isBefore(LocalDate.now())) {
            throw new ReservationException("예약 날짜가 잘못되었습니다");
        }
    }

    private void checkAvailableDate(List<Reservation> reservationList, LocalDate checkIn, LocalDate checkOut) {
        for (Reservation reservation : reservationList) {
            if ((checkIn.isEqual(reservation.getCheckIn()) || checkIn.isAfter(reservation.getCheckIn()) && checkIn.isBefore(reservation.getCheckOut()))
                    || (checkIn.isBefore(reservation.getCheckIn()) && checkOut.isAfter(reservation.getCheckOut()))
                    || (checkOut.isAfter(reservation.getCheckIn()) && (checkOut.isBefore(reservation.getCheckOut()) || checkOut.isEqual(reservation.getCheckOut()))
                    || (checkIn.isEqual(reservation.getCheckIn()) && checkOut.isEqual(reservation.getCheckOut())))) {
                throw new ReservationException("예약이 불가능한 날짜입니다.");
            }
        }
    }


    @GetMapping(produces = MediaTypes.HAL_JSON_VALUE + ";charset=utf8")
    public ResponseEntity<?> getConfirmedReservationLIst(@CurrentUser UserPrincipal userPrincipal,
                                                         Pageable pageable,
                                                         PagedResourcesAssembler<ReservationConfirmedResponse> assembler) {
        User user = userService.findById(userPrincipal.getId());

        Page<Reservation> reservationPage = reservationService.findPageByUser(user, pageable);

        List<Reservation> reservationList = reservationPage.getContent(); //해당 페이지의 모든 컨텐츠
        List<ReservationConfirmedResponse> reservation_confirmedResponseList = reservationService.createResponseList(reservationList);
        PagedModel<EntityModel<ReservationConfirmedResponse>> model = makePageModel(reservation_confirmedResponseList, pageable, reservationPage.getTotalElements(), assembler);

        return ResponseEntity.ok(model);
    }

    private PagedModel<EntityModel<ReservationConfirmedResponse>> makePageModel(List<ReservationConfirmedResponse> reservation_confirmedResponseList,
                                                                                Pageable pageable, Long totalElements,
                                                                                PagedResourcesAssembler<ReservationConfirmedResponse> assembler) {
        Page<ReservationConfirmedResponse> responsePage = new PageImpl<>(reservation_confirmedResponseList, pageable, totalElements);
        PagedModel<EntityModel<ReservationConfirmedResponse>> model = assembler.toModel(responsePage);
        model.add(Link.of("/docs/api.html#resource-reservation-lookupList").withRel("profile"));
        return model;
    }


    @GetMapping(value = "/detail", produces = MediaTypes.HAL_JSON_VALUE + ";charset=utf8")
    public ResponseEntity<?> getDetailReservationInfo(@CurrentUser UserPrincipal userPrincipal, Long reservationId) {
        User user = userService.findById(userPrincipal.getId());
        List<Long> reservationIdUserHave = reservationService.findByUser(user).stream().map(s -> s.getId()).collect(Collectors.toList());

        ReservationDetailResponse reservationDetailResponse = reservationService.judgeReservationIdUserHaveContainReservationId(reservationIdUserHave, reservationId);
        EntityModel<ReservationDetailResponse> model = EntityModel.of(reservationDetailResponse);
        model.add(linkTo(methodOn(ReservationController.class).getDetailReservationInfo(userPrincipal, reservationId)).withSelfRel());
        model.add(Link.of("/docs/api.html#resource-reservation-detail").withRel("profile"));
        return ResponseEntity.ok(model);
    }


    public ReservationDetailResponse ifReservationIdExist(Long reservationId) {
        Reservation reservation = reservationService.findById(reservationId);
        List<BedRoom> bedRoomList = reservation.getRoom().getBedRoomList();
        int bedRoomNum = bedRoomList.size();
        int bedNum = reservation.getRoom().getBedNum();
        ReservationDetailResponse reservation_detail_response = ReservationDetailResponse.builder()
                .hostImage("this is demo host Image URL")
                .roomImage("this is demo room Image URL")
                .bedRoomNum(bedRoomNum)
                .bedNum(bedNum)
                .bathRoomNum(reservation.getRoom().getBathRoomList().size())
                .address(
                        reservation.getRoom().getLocation().getCountry() + " "
                                + reservation.getRoom().getLocation().getCity() + " "
                                + reservation.getRoom().getLocation().getBorough() + " "
                                + reservation.getRoom().getLocation().getNeighborhood() + " "
                                + reservation.getRoom().getLocation().getDetailAddress())
                .latitude(reservation.getRoom().getLocation().getLatitude())
                .longitude(reservation.getRoom().getLocation().getLongitude())
                .checkIn(reservation.getCheckIn())
                .checkOut(reservation.getCheckOut())
                .guestNum(reservation.getGuestNum())
                .hostName(reservation.getRoom().getHost().getName())
                .roomName(reservation.getRoom().getName())
                .isParking(reservation.getRoom().getIsParking())
                .isSmoking(reservation.getRoom().getIsSmoking())
                .roomId(reservation.getRoom().getId())
                .totalCost(reservation.getTotalCost())
                .build();
        return reservation_detail_response;
    }

    @DeleteMapping(produces = MediaTypes.HAL_JSON_VALUE + ";charset=utf8")
    public ResponseEntity<?> deleteReservation(@CurrentUser UserPrincipal userPrincipal,
                                               @RequestBody CancelDto cancelDto) throws Exception {
        User user = userService.findById(userPrincipal.getId());
        List<Reservation> reservationList = reservationService.findByUser(user);

        if (!reservationList.stream().map(Reservation::getId).collect(Collectors.toList()).contains(cancelDto.getReservationId())) {
            throw new ReservationException("there is no reservation that you asked");
        }
        Cancel cancel = Cancel.builder()
                .name(cancelDto.getName())
                .reason(cancelDto.getReason())
                .build();

        reservationService.cancelReservation(cancelDto.getReservationId(), cancel);

        ApiResponse response = ApiResponse.builder()
                .success(true)
                .message("예약 취소 되었습니다")
                .build();

        EntityModel<ApiResponse> model = EntityModel.of(response);
        model.add(linkTo(methodOn(ReservationController.class).deleteReservation(userPrincipal, cancelDto)).withSelfRel());
        model.add(Link.of("/docs/api.html#resource-reservation-delete").withRel("profile"));
        return ResponseEntity.ok(model);
    }
}
