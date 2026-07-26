import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { RoomService } from '../../core/services/room.service';
import { RoomSocketService } from '../../core/services/room-socket.service';

@Component({
  selector: 'app-room',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './room.component.html',
  styleUrl: './room.component.css',
})
export class RoomComponent implements OnInit, OnDestroy {
  roomId!: string;
  loading = signal(true);
  notFound = signal(false);

  myName = signal<string | null>(null);
  nameInput = '';
  myVote = signal<string | number | null>(null);
  linkCopied = signal(false);

  roomState;
  connected;

  constructor(
    private route: ActivatedRoute,
    private roomService: RoomService,
    private roomSocket: RoomSocketService,
    private router: Router,
  ) {
    this.roomState = this.roomSocket.state;
    this.connected = this.roomSocket.connected;
  }

  ngOnInit(): void {
    this.roomId = this.route.snapshot.paramMap.get('roomId')!;

    this.roomService.checkRoom(this.roomId).subscribe({
      next: () => {
        this.loading.set(false);
        const savedName = sessionStorage.getItem(this.storageKey());
        if (savedName) {
          this.joinAs(savedName);
        }
      },
      error: () => {
        this.loading.set(false);
        this.notFound.set(true);
      },
    });
  }

  ngOnDestroy(): void {
    this.roomSocket.disconnect();
  }

  submitName(): void {
    const trimmed = this.nameInput.trim();
    if (!trimmed) return;
    sessionStorage.setItem(this.storageKey(), trimmed);
    this.joinAs(trimmed);
  }

  private joinAs(name: string): void {
    this.myName.set(name);
    this.roomSocket.connect(this.roomId, name);
  }

  vote(value: number | string): void {
    this.myVote.set(value);
    this.roomSocket.vote(value);
  }

  reveal(): void {
    this.roomSocket.reveal();
  }

  resetVotes(): void {
    this.myVote.set(null);
    this.roomSocket.reset();
  }

  leaveVote() {
    this.roomSocket.disconnect();
    this.router.navigate(['/']);
  }

  copyLink(): void {
    navigator.clipboard.writeText(window.location.href).then(() => {
      this.linkCopied.set(true);
      setTimeout(() => this.linkCopied.set(false), 2000);
    });
  }

  initials(name: string): string {
    return name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  }

  private storageKey(): string {
    return `poker_name_${this.roomId}`;
  }
}
