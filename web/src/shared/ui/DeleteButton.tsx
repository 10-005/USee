import { Button, type ButtonProps } from './Button';

export function DeleteButton(props: Omit<ButtonProps, 'children' | 'className'>) {
  return (
    <Button {...props} className="work-delete-button">
      <svg
        aria-hidden="true"
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M4 7h16M10 11v6m4-6v6M5 7l1 13h12l1-13M9 7V4h6v3" />
      </svg>
      삭제하기
    </Button>
  );
}
