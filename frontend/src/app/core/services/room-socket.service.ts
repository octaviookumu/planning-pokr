import { Injectable, signal } from '@angular/core';
import { RoomState } from '../models/models';
import { WS_BASE_URL } from '../config';

@Injectable({ providedIn: 'root' })
export class RoomSocketService {
  state = signal<RoomState | null>(null);
  connected = signal(false);

  private socket: WebSocket | null = null;

  connect(roomId: string, name: string): void {
    this.disconnect();
    const url = `${WS_BASE_URL}/ws/rooms/${roomId}?name=${encodeURIComponent(name)}`;
    this.socket = new WebSocket(url);

    this.socket.onopen = () => this.connected.set(true);
    this.socket.onclose = () => this.connected.set(false);
    this.socket.onerror = () => this.connected.set(false);
    this.socket.onmessage = (event) => {
      const data = JSON.parse(event.data) as RoomState;
      this.state.set(data);
    };
  }

  vote(value: number | string): void {
    this.send({ action: 'vote', value });
  }

  reveal(): void {
    this.send({ action: 'reveal' });
  }

  reset(): void {
    this.send({ action: 'reset' });
  }

  private send(payload: unknown): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(payload));
    }
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.onmessage = null;
      this.socket.onclose = null;
      this.socket.close();
      this.socket = null;
    }
    this.state.set(null);
    this.connected.set(false);
  }
}
