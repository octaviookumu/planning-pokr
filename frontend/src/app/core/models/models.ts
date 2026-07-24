export interface RoomInfo {
  id: string;
  created_at: string;
}

export interface Participant {
  name: string;
  has_voted: boolean;
  value: string | null;
}

export interface RoomState {
  type: 'state';
  room_id: string;
  revealed: boolean;
  participants: Participant[];
  card_values: (number | string)[];
}
