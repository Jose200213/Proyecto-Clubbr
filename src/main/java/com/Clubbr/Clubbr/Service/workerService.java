package com.Clubbr.Clubbr.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.Clubbr.Clubbr.Dto.workerDto;
import com.Clubbr.Clubbr.Entity.*;

import com.Clubbr.Clubbr.advice.*;

import com.Clubbr.Clubbr.config.exception.BadRequestException;
import com.Clubbr.Clubbr.utils.attendance;
import com.Clubbr.Clubbr.utils.role;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.Clubbr.Clubbr.Repository.workerRepo;

import com.Clubbr.Clubbr.Repository.stablishmentRepo;
import com.Clubbr.Clubbr.Repository.userRepo;
import com.Clubbr.Clubbr.Repository.managerRepo;
import com.Clubbr.Clubbr.Repository.eventRepo;
import org.springframework.transaction.annotation.Transactional;

import com.Clubbr.Clubbr.Repository.interestPointRepo;


@Service
public class workerService {

    @Autowired
    private workerRepo workerRepo;

    @Autowired
    private eventRepo eventRepo;

    @Autowired
    private stablishmentRepo stablishmentRepo;

    @Autowired
    private userService userService;


    @Autowired
    private managerService managerService;

    @Autowired
    private jwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private stablishmentService stablishmentService;

    @Autowired
    private interestPointService interestPointService;

    @Autowired
    private interestPointRepo interestPointRepo;

    @Autowired
    private eventService eventService;

    @Autowired
    private userRepo userRepo;

    public List<worker> getAllWorkersFromStab(Long stablishmentID, String token) {
        managerService.checkManagerIsFromStab(stablishmentID, token);

        List<worker> workers = workerRepo.findAllByStablishmentID(stablishmentService.getStab(stablishmentID));
        if (workers.isEmpty()) {
            throw new ResourceNotFoundException("Trabajadores");
        }
        return workers;
    }

    public List<worker> getAllWorkersFromEvent(Long stablishmentID, String eventName, LocalDate eventDate, String token) {
        managerService.checkManagerIsFromStab(stablishmentID, token);
        stablishment targetStablishment = stablishmentService.getStab(stablishmentID);
        event targetEvent = eventService.getEventByStabNameDate(targetStablishment.getStablishmentID(), eventName, eventDate);


        List<worker> workers = workerRepo.findAllByStablishmentIDAndEventIDOrStablishmentIDAndEventIDIsNull(
                targetStablishment, targetEvent, targetStablishment);
        if (workers.isEmpty()) {
            throw new ResourceNotFoundException("Trabajadores");
        }
        return workers;
    }

    public worker getWorkerFromEvent(String userID, Long stablishmentID, String eventName, LocalDate eventDate, String token) {
        managerService.checkManagerIsFromStab(stablishmentID, token);
        stablishment targetStablishment = stablishmentService.getStab(stablishmentID);
        user targetUser = userService.getUser(userID);
        event targetEvent = eventService.getEventByStabNameDate(targetStablishment.getStablishmentID(), eventName, eventDate);


        return workerRepo.findByEventIDAndUserID(targetEvent, targetUser)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", "userID", targetUser.getUserID(), "Establecimiento", "stablishmentID", targetStablishment.getStablishmentID()));

    }

    public List<worker> getAllWorkersFromUser(user userID) {
        List<worker> workers = workerRepo.findAllByUserID(userID);
        if (workers.isEmpty()) {
            throw new ResourceNotFoundException("Trabajadores");
        }
        return workers;
    }

    public List<workerDto> getWorkersListDto(List<worker> workers){
        return workers.stream().map(workerDto::new).collect(Collectors.toList());
    }

    public workerDto getWorkerDto(worker worker){
        return new workerDto(worker);
    }

    public List<worker> getAllWorkers() {
        List<worker> workers = workerRepo.findAll();
        if (workers.isEmpty()) {
            throw new ResourceNotFoundException("Trabajadores");
        }
        return workers;
    }

    public List<worker> getWorkersWithNullEventID() {
        return workerRepo.findAllByEventIDIsNull();
    }


    public worker getWorker(user userID, stablishment stablishmentID) {
        return workerRepo.findByUserIDAndStablishmentID(userID, stablishmentID)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", "userID", userID.getUserID(), "Establecimiento", "stablishmentID", stablishmentID.getStablishmentID()));

    }

    public worker getWorkerFromStab(String userID, Long stablishmentID, String token) {
        managerService.checkManagerIsFromStab(stablishmentID, token);
        user targetUser = userService.getUser(userID);
        user requestUser = userService.getUser(jwtService.extractUserIDFromToken(token));

        if (requestUser.getUserRole() == role.WORKER){
            if (!targetUser.equals(requestUser)){
                throw new ResourceNotFoundException("Trabajador", "userID", targetUser.getUserID());
            }
        }

        return getWorker(targetUser, stablishmentService.getStab(stablishmentID));
    }

    @Transactional
    public void updateAttendance(String telegramID, boolean att, String eventName, LocalDate eventDate, Long stabID) {

        user targetUser = userRepo.findByTelegramID(Long.parseLong(telegramID));
        stablishment stab = stablishmentRepo.findById(stabID).orElse(null);
        event existingEvent = eventService.getEventByStabNameDate(stab.getStablishmentID(), eventName, eventDate);

        worker workerToUpdate = workerRepo.findByUserIDAndEventIDAndStablishmentID(targetUser, existingEvent, stab);
        if(att) workerToUpdate.setAttendance(attendance.TRUE);
        else workerToUpdate.setAttendance(attendance.FALSE);
        workerRepo.save(workerToUpdate);

    }

    @Transactional
    public void addWorkerToStab(Long stablishmentID, worker targetWorker, String token){
        managerService.checkManagerIsFromStab(stablishmentID, token);
        stablishment targetStab = stablishmentService.getStab(stablishmentID);
        user targetUser = userService.getUser(targetWorker.getUserID().getUserID());

        targetUser.setUserRole(role.WORKER);
        targetWorker.setUserID(targetUser);
        targetWorker.setStablishmentID(targetStab);


        if (targetWorker.getEventID() != null){
            worker workerFlag = workerRepo.findByUserIDAndStablishmentID(targetUser, targetStab).orElse(null);
            //Comprobacion en caso de que el usuario sea ya trabajador fijo del establecimiento y se haya intentado introducir de nuevo con un evento como eventual.
            if (workerFlag != null && workerFlag.getEventID() == null){
                throw new BadRequestException("El usuario ya es trabajador fijo del establecimiento.");
            }
            if (workerRepo.existsByUserIDAndEventIDAndStablishmentID(targetWorker.getUserID(), targetWorker.getEventID(), targetWorker.getStablishmentID())) {
                throw new BadRequestException("El trabajador especificado ya se encuentra en el establecimiento como trabajador fijo o en el evento especificado");
            }
            event targetEvent = eventService.getEventByStabNameDate(targetStab.getStablishmentID(), targetWorker.getEventID().getEventName(), targetWorker.getEventID().getEventDate());
            targetWorker.setEventID(targetEvent);
            targetEvent.getWorkers().add(targetWorker);
            targetWorker.setAttendance(attendance.PENDING);


        }else{
            worker workerFlag = workerRepo.findByUserIDAndStablishmentID(targetUser, targetStab).orElse(null);
            if (workerFlag != null){
                throw new BadRequestException("El usuario ya es fijo trabajador del establecimiento.");
            }
            targetWorker.setWorkingHours(160L);
            targetWorker.setAttendance(attendance.TRUE);
            targetWorker.setEventID(null);
        }


        workerRepo.save(targetWorker);
        userRepo.save(targetUser);

    }

    public void addWorkerToStabInterestPoint(Long stablishmentID, String userID, Long interestPointID, String token){
        managerService.checkManagerIsFromStab(stablishmentID, token);
        stablishment targetStab = stablishmentService.getStab(stablishmentID);
        worker worker = getWorker(userService.getUser(userID), targetStab);
        interestPoint interestPoint = interestPointService.getInterestPointByStablishment(targetStab.getStablishmentID(), interestPointID);


        worker.setInterestPointID(interestPoint);
        interestPoint.getWorkers().add(worker);
        interestPointRepo.save(interestPoint);
        workerRepo.save(worker);
    }

    public void addWorkerToEventInterestPoint(Long stablishmentID, String eventName, LocalDate eventDate,String userID, Long interestPointID, String token){
        managerService.checkManagerIsFromStab(stablishmentID, token);
        stablishment targetStab = stablishmentService.getStab(stablishmentID);
        worker worker = getWorker(userService.getUser(userID), targetStab);
        event event = eventService.getEventByStabNameDate(targetStab.getStablishmentID(), eventName, eventDate);
        interestPoint interestPoint = interestPointService.getInterestPointByEventName(stablishmentID, event.getEventName(), event.getEventDate(),interestPointID);


        worker.setInterestPointID(interestPoint);
        interestPoint.getWorkers().add(worker);
        interestPointRepo.save(interestPoint);
        workerRepo.save(worker);
    }

    public void deleteWorkerFromStab(Long stablishmentID, String userID, String token){
        managerService.checkManagerIsFromStab(stablishmentID, token);
        stablishment targetStab = stablishmentService.getStab(stablishmentID);
        worker worker = getWorker(userService.getUser(userID), targetStab);


        if (getAllWorkersFromUser(worker.getUserID()).size() == 1){
            worker.getUserID().setUserRole(role.USER);
            userRepo.save(worker.getUserID());
        }

        workerRepo.delete(worker);
    }

    public void updateWorker(Long stablishmentID, worker targetWorker, String token) {
        managerService.checkManagerIsFromStab(stablishmentID, token);
        worker workerToUpdate = workerRepo.findById(targetWorker.getId()).orElseThrow(() -> new ResourceNotFoundException("Trabajador", "id", targetWorker.getId()));

        workerToUpdate.setInterestPointID(targetWorker.getInterestPointID());
        workerToUpdate.setSalary(targetWorker.getSalary());
        workerToUpdate.setWorkingHours(targetWorker.getWorkingHours());
        workerRepo.save(workerToUpdate);
    }

}
