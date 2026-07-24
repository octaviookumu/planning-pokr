import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config';
import { RoomInfo } from '../models/models';

@Injectable({ providedIn: 'root' })
export class RoomService {
  constructor(private http: HttpClient) {}

  createRoom(): Observable<RoomInfo> {
    return this.http.post<RoomInfo>(`${API_BASE_URL}/rooms`, {});
  }

  checkRoom(id: string): Observable<RoomInfo> {
    return this.http.get<RoomInfo>(`${API_BASE_URL}/rooms/${id}`);
  }
}
