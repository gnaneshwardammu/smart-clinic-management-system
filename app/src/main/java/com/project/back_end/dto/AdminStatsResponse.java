package com.project.back_end.dto;

public record AdminStatsResponse(long totalDoctors, long activeDoctors, long totalPatients,
                                 long totalAppointments, long scheduledAppointments,
                                 long completedAppointments, long cancelledAppointments,
                                 long appointmentsToday) {
}
