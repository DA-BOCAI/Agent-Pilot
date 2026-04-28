type EmptyStateProps = {
  detail: string
  title: string
}

export function EmptyState({ detail, title }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{detail}</p>
    </div>
  )
}
