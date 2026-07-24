import { Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { RoomService } from '../../core/services/room.service';

@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  creating = signal(false);

  constructor(private roomService: RoomService, private router: Router) {}

  createRoom(): void {
    this.creating.set(true);
    this.roomService.createRoom().subscribe({
      next: (room) => this.router.navigate(['/rooms', room.id]),
      error: () => this.creating.set(false),
    });
  }
}
