package com.Clubbr.Clubbr.Service;

import com.Clubbr.Clubbr.Dto.eventDto;
import com.Clubbr.Clubbr.Entity.*;
import com.Clubbr.Clubbr.Repository.eventRepo;
import com.Clubbr.Clubbr.Repository.workerRepo;
import com.Clubbr.Clubbr.advice.ResourceNotFoundException;
import com.Clubbr.Clubbr.config.exception.BadRequestException;
import com.Clubbr.Clubbr.config.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class eventService {

    @Autowired
    private eventRepo eventRepo;

    @Autowired
    private MqttClient mqttClient;

    @Autowired
    private jwtService jwtService;


    @Autowired
    private userService userService;


    @Autowired
    private managerService managerService;

    @Autowired
    private stablishmentService stablishmentService;

    @Autowired
    private workerRepo workerRepo;


    @Transactional
    public void addEventToStab(Long stabID, event newEvent, String token) {
        managerService.checkManagerIsFromStab(stabID, token);
        stablishment stab = stablishmentService.getStab(stabID);
        event eventFlag = eventRepo.findByStablishmentIDAndEventNameAndEventDate(stab, newEvent.getEventName(), newEvent.getEventDate()).orElse(null);

        if (eventFlag != null) {

            throw new BadRequestException("Event with name: " + newEvent.getEventName() + " and date: " + newEvent.getEventDate() + " already exists");

        }


        newEvent.setStablishmentID(stab);
        newEvent.setTotalTickets(stab.getCapacity());

        if (newEvent.getInterestPoints() != null) {


            List<interestPoint> iPsToStore = new ArrayList<>();

            for (interestPoint ip : newEvent.getInterestPoints()) {

                interestPoint interestPointAux = new interestPoint();
                interestPointAux.setStablishmentID(stab);
                interestPointAux.setEventName(newEvent);
                interestPointAux.setXCoordinate(ip.getXCoordinate());
                interestPointAux.setYCoordinate(ip.getYCoordinate());
                interestPointAux.setDescription(ip.getDescription());
                iPsToStore.add(interestPointAux);

            }
            newEvent.setInterestPoints(iPsToStore);
        }

        eventRepo.save(newEvent);
    }


    @Transactional(readOnly = true)
    public List<event> getAllEventsOrderedByDateInStab(Long stabID) {
        stablishment stab = stablishmentService.getStab(stabID);
        return eventRepo.findAllByStablishmentIDAndEventDateAfterOrderByEventDateAsc(stab, LocalDate.now());
    }

    public List<event> getAllEvents() {
        return eventRepo.findAllOrderByEventDateAfterOrderByEventDateAsc(LocalDate.now());
    }

    public List<eventDto> getEventsListDto(List<event> events) {
        return events.stream().map(eventDto::new).collect(java.util.stream.Collectors.toList());
    }

    public eventDto getEventDto(event event) {
        return new eventDto(event);
    }

    @Transactional(readOnly = true)
    public event getEventByStabNameDate(Long stabID, String name, LocalDate date) {
        stablishment stab = stablishmentService.getStab(stabID);
        return eventRepo.findByStablishmentIDAndEventNameAndEventDate(stab, name, date).orElseThrow(() -> new ResourceNotFoundException("Event", "eventName", name, "Establecimiento", "stablishmentID", stabID));
    }

    @Transactional
    public void updateEventFromStablishment(Long stabID, String eventName, LocalDate eventDate, event targetEvent, String token) {
        managerService.checkManagerIsFromStab(stabID, token);
        stablishment stab = stablishmentService.getStab(stabID);
        event existingEvent = eventRepo.findByStablishmentIDAndEventNameAndEventDate(stab, eventName, eventDate).orElse(null);

        if (existingEvent == null) {
            throw new NotFoundException("Event to update not found");
        }

        event eventFlag = eventRepo.findByStablishmentIDAndEventNameAndEventDate(stab, targetEvent.getEventName(), targetEvent.getEventDate()).orElse(null);

        if (eventFlag != null) {
            throw new BadRequestException("Can't update an Event with name: " + targetEvent.getEventName() + " and date: " + targetEvent.getEventDate() + " already exists");
        }

        // Crea un nuevo evento con los datos actualizados
        event newEvent = new event();
        newEvent.setEventName(targetEvent.getEventName());
        newEvent.setEventDate(targetEvent.getEventDate());
        newEvent.setEventFinishDate(targetEvent.getEventFinishDate());
        newEvent.setEventDescription(targetEvent.getEventDescription());
        newEvent.setEventPrice(targetEvent.getEventPrice());
        newEvent.setEventTime(targetEvent.getEventTime());
        newEvent.setTotalTickets(stab.getCapacity());
        newEvent.setStablishmentID(stab);

        // Guarda el nuevo evento
        eventRepo.save(newEvent);

        // Elimina el evento existente
        eventRepo.delete(existingEvent);
    }

    @Transactional
    public void deleteEventFromStablishment(Long stabID, String eventName, LocalDate eventDate, String token) {
        managerService.checkManagerIsFromStab(stabID, token);
        event existingEvent = eventRepo.findByStablishmentIDAndEventNameAndEventDate(stablishmentService.getStab(stabID), eventName, eventDate).orElse(null);

        if (existingEvent == null) {
            throw new NotFoundException("Event to delete not found");
        }


        eventRepo.delete(existingEvent);
    }

    //////////////////////////////////////////// FUNCION AÑADE EVENTOS PERSISTENTES CON UNA FRECUENCIA PREDETERMINADA DE UNA SEMANA (7 DIAS) No usa Dto////////////////////////////////////////////
    @Transactional
    public void addPersistentEventToStab(Long stabID, int repeticiones, event newEvent, String token) {
        managerService.checkManagerIsFromStab(stabID, token);
        stablishment stab = stablishmentService.getStab(stabID);
        //event eventAux = new event();
        event eventFlag = eventRepo.findByStablishmentIDAndEventNameAndEventDate(stab, newEvent.getEventName(), newEvent.getEventDate()).orElse(null);

        if (eventFlag != null) {

            throw new BadRequestException("Event with name: " + newEvent.getEventName() + " and date: " + newEvent.getEventDate() + " already exists");

        }



        newEvent.setStablishmentID(stab);
        newEvent.setTotalTickets(stab.getCapacity());
        newEvent.setEventTime(stab.getOpenTime().toString());

        int i = 0;

        do {

            eventRepo.save(newEvent);
            newEvent.setEventDate(newEvent.getEventDate().plusDays(7));
            newEvent.setEventFinishDate(newEvent.getEventFinishDate().plusDays(7));
            i++;

        } while (i < repeticiones);

    }

//////////////////////////////////////////// FIN FUNCION AÑADE EVENTOS PERSISTENTES CON UNA FRECUENCIA PREDETERMINADA DE UNA SEMANA (7 DIAS) ////////////////////////////////////////////


    @Transactional
    public void attendanceControlWorkers(Long stabID, String eventName, LocalDate eventDate, String token) throws JsonProcessingException, MqttException {
        managerService.checkManagerIsFromStab(stabID, token);
        List<worker> workers = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        stablishment stab = stablishmentService.getStab(stabID);
        event existingEvent = getEventByStabNameDate(stab.getStablishmentID(), eventName, eventDate);


        workers = workerRepo.findAllByStablishmentID(stab);
        if (workers.isEmpty()) {
            throw new ResourceNotFoundException("Trabajadores");
        }

        List<ObjectNode> jsonList = new ArrayList<>();

        for(worker worker : workers){
            if(worker.getEventID() == existingEvent){
                ObjectNode json = objectMapper.createObjectNode();
                json.put("Date", existingEvent.getEventDate().toString());
                json.put("Time", stab.getOpenTime().toString());
                json.put("StabName", stab.getStabName());
                json.put("StabAddress", stab.getStabAddress());
                json.put("EventName", existingEvent.getEventName());
                json.put("StabId", stab.getStablishmentID());
                json.put("TelegramID", userService.getUser(worker.getUserID().getUserID()).getTelegramID());

                jsonList.add(json);
            }
        }

        String jsonString = objectMapper.writeValueAsString(jsonList);

        byte[] payload = jsonString.getBytes();
        MqttMessage mqttMessage = new MqttMessage(payload);

        if (mqttClient != null) {
            mqttClient.publish("Clubbr/AttendanceControl", mqttMessage);
        } else {
            System.err.println("No se puede publicar el mensaje porque el cliente MQTT no está disponible");
        }
    }


}

